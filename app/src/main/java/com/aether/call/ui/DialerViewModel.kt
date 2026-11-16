package com.aether.call.ui

import android.app.role.RoleManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aether.call.data.AetherDatabase
import com.aether.call.data.AppSettingsManager
import com.aether.call.data.BlockedNumberEntity
import com.aether.call.data.CallLogItem
import com.aether.call.data.CallLogRepository
import com.aether.call.data.CallRecord
import com.aether.call.data.ContactAccount
import com.aether.call.data.ContactFtsEntity
import com.aether.call.data.ContactItem
import com.aether.call.data.ContactNoteEntity
import com.aether.call.data.ContactsRepository
import com.aether.call.data.DetailedContact
import com.aether.call.data.LabeledEntry
import com.aether.call.data.LabeledValue
import com.aether.call.data.PosterCustomization
import com.aether.call.data.QuickReplyEntity
import com.aether.call.util.showToast
import kotlinx.coroutines.withContext
import com.aether.call.data.SimPreferenceEntity
import com.aether.call.data.UserAppSettings
import com.aether.call.telecom.SimAccountInfo
import com.aether.call.telecom.SimManager
import com.aether.call.util.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class NavigationTab {
    KEYPAD,
    RECENTS,
    CONTACTS;

    companion object {
        val DIALER = KEYPAD
    }
}

data class CallTarget(
    val number: String,
    val contactName: String? = null,
    val isVideo: Boolean = false,
)

typealias PendingSimCall = CallTarget

class DialerViewModel : ViewModel() {

    private val starredOverrides = java.util.concurrent.ConcurrentHashMap<Long, Boolean>()

    private val _enteredNumber = MutableStateFlow("")
    val enteredNumber: StateFlow<String> = _enteredNumber.asStateFlow()

    private val _recentCalls = MutableStateFlow<List<CallRecord>>(emptyList())
    val recentCalls: StateFlow<List<CallRecord>> = _recentCalls.asStateFlow()

    // Navigation tracking
    val currentTab = MutableStateFlow(NavigationTab.KEYPAD)
    private val tabBackStack = mutableListOf(NavigationTab.KEYPAD)

    // Tab index mapping: 0 = Recents, 1 = Dialer, 2 = Contacts
    private val _selectedTab = MutableStateFlow(1)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    val isDefaultDialer = MutableStateFlow(false)

    val contactsList = MutableStateFlow<List<ContactItem>>(emptyList())
    val contacts: StateFlow<List<ContactItem>> = contactsList.asStateFlow()
    val isLoadingContacts = MutableStateFlow(false)

    /**
     * Live callLogs flow automatically cross-referenced with contactsList
     * using normalized 10-digit matching so saved names update everywhere instantaneously.
     */
    val callLogs: StateFlow<List<CallLogItem>> = combine(
        _recentCalls,
        contactsList
    ) { records, allContacts ->
        val contactByDigits = HashMap<String, ContactItem>(allContacts.size)
        for (c in allContacts) {
            for (p in c.phones) {
                val norm = PhoneNormalizer.normalizeDigits(p.value)
                if (norm.isNotEmpty() && !contactByDigits.containsKey(norm)) {
                    contactByDigits[norm] = c
                }
            }
        }

        records.map { record ->
            val norm = PhoneNormalizer.normalizeDigits(record.number)
            val matchedContact = if (norm.isNotEmpty()) contactByDigits[norm] else null
            val resolvedName = matchedContact?.displayName?.takeIf { it.isNotBlank() }
                ?: record.name?.takeIf { it.isNotBlank() }

            CallLogItem(
                id = record.id,
                name = resolvedName,
                number = record.number,
                type = record.type,
                dateMillis = record.date,
                durationSeconds = record.duration,
                simDisplayName = record.simDisplayName,
                repeatCount = 1,
                isWifiCall = true,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val searchQuery = MutableStateFlow("")
    val ftsSearchQuery = MutableStateFlow("")
    val selectedFilterChip = MutableStateFlow("All")

    val userSettings = MutableStateFlow(UserAppSettings())

    val filteredContacts: StateFlow<List<ContactItem>> = combine(
        contactsList,
        searchQuery,
        userSettings,
    ) { all, query, settings ->
        var list = if (settings.hideContactsWithoutNumbers) {
            all.filter { it.phones.any { p -> p.value.isNotBlank() } }
        } else {
            all
        }
        if (query.isNotBlank()) {
            val qLower = query.trim().lowercase()
            val qDigits = query.filter { it.isDigit() }
            list = list.filter { contact ->
                contact.displayName.lowercase().contains(qLower) ||
                        contact.phones.any { p ->
                            p.value.contains(query) || (qDigits.isNotEmpty() && p.value.filter { it.isDigit() }.contains(qDigits))
                        } ||
                        contact.emails.any { it.value.lowercase().contains(qLower) }
            }
        }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList(),
    )

    val selectedContactForEdit = MutableStateFlow<ContactItem?>(null)
    val isCreateContactOpen = MutableStateFlow(false)
    val isEditingContact = MutableStateFlow(false)
    val isEditingMyProfile = MutableStateFlow(false)

    val showPosterCustomizer = MutableStateFlow(false)
    val showSettingsScreen = MutableStateFlow(false)
    val activeDetailedContact = MutableStateFlow<DetailedContact?>(null)

    val availableAccounts = MutableStateFlow<List<ContactAccount>>(emptyList())
    val selectedAccount = MutableStateFlow<ContactAccount?>(null)

    val starredContacts = MutableStateFlow<List<ContactItem>>(emptyList())
    val quickReplies = MutableStateFlow<List<QuickReplyEntity>>(emptyList())
    val myProfile = MutableStateFlow(
        ContactItem(
            id = -1L,
            prefix = "Mr.",
            firstName = "Naresh Kumar",
            lastName = "Meena",
            displayName = "Mr. Naresh Kumar Meena",
        ),
    )

    val activeContactCard = MutableStateFlow<ContactItem?>(null)
    val isMyProfileCardOpen = MutableStateFlow(false)
    val activeContactHistory = MutableStateFlow<List<CallRecord>>(emptyList())

    val activeContactNote = MutableStateFlow("")
    val activeContactAncLevel = MutableStateFlow(0.8f)
    val activeContactPreferredSim = MutableStateFlow(1)

    val activeSims = MutableStateFlow<List<SimAccountInfo>>(emptyList())
    val activeSimList = MutableStateFlow<List<SimAccountInfo>>(emptyList())
    val simSelectionDialog = MutableStateFlow<CallTarget?>(null)

    val blockedList = MutableStateFlow<List<BlockedNumberEntity>>(emptyList())

    private var observersRegistered = false
    private var syncDebounceJob: Job? = null

    val canNavigateBack: StateFlow<Boolean> = combine(
        showPosterCustomizer,
        showSettingsScreen,
        isEditingContact,
        activeDetailedContact,
        activeContactCard,
        isMyProfileCardOpen,
        searchQuery,
        enteredNumber,
        currentTab,
    ) { flows: Array<Any?> ->
        val customizer = flows[0] as Boolean
        val settings = flows[1] as Boolean
        val editing = flows[2] as Boolean
        val detailed = flows[3]
        val card = flows[4]
        val myProfOpen = flows[5] as Boolean
        val query = flows[6] as String
        val number = flows[7] as String
        val tab = flows[8] as NavigationTab

        customizer || settings || editing || detailed != null || card != null || myProfOpen ||
                query.isNotEmpty() || number.isNotEmpty() || tab != NavigationTab.KEYPAD
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false,
    )

    /**
     * Registers live system ContentObservers so any contact saved or edited in
     * WhatsApp, Telegram, Google Contacts, or AetherCall syncs automatically.
     */
    fun startLiveSyncObservers(context: Context) {
        if (observersRegistered) return
        observersRegistered = true
        val appContext = context.applicationContext

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                syncDebounceJob?.cancel()
                syncDebounceJob = viewModelScope.launch(Dispatchers.IO) {
                    delay(250L)
                    refreshAllDataSync(appContext)
                }
            }
        }

        try {
            appContext.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer
            )
            appContext.contentResolver.registerContentObserver(
                ContactsContract.RawContacts.CONTENT_URI,
                true,
                observer
            )
            appContext.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                observer
            )
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private suspend fun refreshAllDataSync(context: Context) {
        val loadedContacts = ContactsRepository.loadAllContacts(context)
        contactsList.value = loadedContacts
        starredContacts.value = loadedContacts.filter { it.isStarred }

        val calls = CallLogRepository.getRecentCalls(context)
        _recentCalls.value = calls

        // Keep open Contact Card synchronized with latest fields
        val openCard = activeContactCard.value
        if (openCard != null && openCard.id > 0L) {
            loadedContacts.find { it.id == openCard.id }?.let { freshContact ->
                activeContactCard.value = freshContact
            }
        }
    }

    fun selectTab(tab: NavigationTab) {
        if (searchQuery.value.isNotEmpty()) {
            searchQuery.value = ""
            ftsSearchQuery.value = ""
        }
        if (currentTab.value != tab) {
            tabBackStack.remove(tab)
            tabBackStack.add(tab)
            currentTab.value = tab
            _selectedTab.value = when (tab) {
                NavigationTab.RECENTS -> 0
                NavigationTab.KEYPAD -> 1
                NavigationTab.CONTACTS -> 2
            }
        }
    }

    fun selectTab(tabIndex: Int) {
        val target = when (tabIndex) {
            0 -> NavigationTab.RECENTS
            1 -> NavigationTab.KEYPAD
            2 -> NavigationTab.CONTACTS
            else -> NavigationTab.KEYPAD
        }
        selectTab(target)
    }

    fun handleBackNavigation(): Boolean {
        if (showSettingsScreen.value) {
            showSettingsScreen.value = false
            return true
        }
        if (showPosterCustomizer.value) {
            showPosterCustomizer.value = false
            return true
        }
        if (isCreateContactOpen.value || isEditingContact.value || isEditingMyProfile.value) {
            closeContactSheet()
            return true
        }
        if (isMyProfileCardOpen.value) {
            isMyProfileCardOpen.value = false
            return true
        }
        if (activeDetailedContact.value != null) {
            activeDetailedContact.value = null
            return true
        }
        if (activeContactCard.value != null) {
            closeContactCard()
            return true
        }
        if (searchQuery.value.isNotEmpty()) {
            searchQuery.value = ""
            ftsSearchQuery.value = ""
            return true
        }
        if (enteredNumber.value.isNotEmpty()) {
            clear()
            return true
        }
        if (currentTab.value != NavigationTab.KEYPAD) {
            tabBackStack.remove(currentTab.value)
            val previousTab = tabBackStack.lastOrNull() ?: NavigationTab.KEYPAD
            currentTab.value = previousTab
            _selectedTab.value = when (previousTab) {
                NavigationTab.RECENTS -> 0
                NavigationTab.KEYPAD -> 1
                NavigationTab.CONTACTS -> 2
            }
            return true
        }
        return false
    }

    fun appendDigit(digit: String) {
        _enteredNumber.value += digit
    }

    fun deleteDigit() {
        if (_enteredNumber.value.isNotEmpty()) {
            _enteredNumber.value = _enteredNumber.value.dropLast(1)
        }
    }

    fun clear() {
        _enteredNumber.value = ""
    }

    fun clearEnteredNumber() = clear()
    fun deleteLastDigit() = deleteDigit()

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun updateFtsSearchQuery(query: String) {
        ftsSearchQuery.value = query
    }

    fun loadQuickReplies(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AetherDatabase.getDatabase(context).quickReplyDao()
            dao.getAllTemplates().collect { list ->
                quickReplies.value = list
            }
        }
    }

    fun addQuickReply(context: Context, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AetherDatabase.getDatabase(context).quickReplyDao()
            val currentMax = quickReplies.value.maxOfOrNull { it.sortOrder } ?: 0
            dao.insertTemplate(
                QuickReplyEntity(messageText = text.trim(), sortOrder = currentMax + 1),
            )
        }
    }

    fun updateQuickReply(context: Context, template: QuickReplyEntity, newText: String) {
        if (newText.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AetherDatabase.getDatabase(context).quickReplyDao()
            dao.updateTemplate(template.copy(messageText = newText.trim()))
        }
    }

    fun deleteQuickReply(context: Context, template: QuickReplyEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AetherDatabase.getDatabase(context).quickReplyDao()
            dao.deleteTemplate(template)
        }
    }

    fun loadAppSettings(context: Context) {
        startLiveSyncObservers(context)
        viewModelScope.launch(Dispatchers.IO) {
            AppSettingsManager(context).settingsFlow.collect { settings ->
                userSettings.value = settings
                if (settings.openToKeypad && currentTab.value != NavigationTab.KEYPAD) {
                    currentTab.value = NavigationTab.KEYPAD
                }
            }
        }
    }

    fun loadMyProfile(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            AppSettingsManager(context).myProfileFlow.collect { profile ->
                myProfile.value = profile
            }
        }
    }

    fun openMyProfileCard() {
        isMyProfileCardOpen.value = true
    }

    fun closeMyProfileCard() {
        isMyProfileCardOpen.value = false
    }

    fun updateMyProfile(context: Context, updated: ContactItem) {
        val normalizedProfile = updated.copy(id = -1L)
        myProfile.value = normalizedProfile
        isEditingMyProfile.value = false
        isCreateContactOpen.value = false
        viewModelScope.launch(Dispatchers.IO) {
            AppSettingsManager(context).updateMyProfile(normalizedProfile)
        }
    }

    fun updateAppThemeMode(context: Context, mode: String) = viewModelScope.launch(Dispatchers.IO) {
        AppSettingsManager(context).updateAppThemeMode(mode)
    }

    fun updateDefaultSim(context: Context, slot: Int) = viewModelScope.launch(Dispatchers.IO) {
        AppSettingsManager(context).setDefaultSimSlot(slot)
    }

    fun updateAncLevel(context: Context, level: Float) = viewModelScope.launch(Dispatchers.IO) {
        AppSettingsManager(context).setAncLevel(level)
    }

    fun updateHapticsEnabled(context: Context, enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        AppSettingsManager(context).setHapticsEnabled(enabled)
    }

    fun updateHapticStrength(context: Context, strength: String) = viewModelScope.launch(Dispatchers.IO) {
        AppSettingsManager(context).setHapticStrength(strength)
    }

    fun updateFlipToSilence(context: Context, enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        AppSettingsManager(context).setFlipToSilence(enabled)
    }

    fun updateSpamShield(context: Context, enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        AppSettingsManager(context).setSpamShieldEnabled(enabled)
    }

    fun updateCallDisplayMode(context: Context, mode: String) = viewModelScope.launch(Dispatchers.IO) {
        AppSettingsManager(context).setCallDisplayMode(mode)
    }

    fun updateAutoRecordCalls(context: Context, enabled: Boolean) = viewModelScope.launch(Dispatchers.IO) {
        AppSettingsManager(context).setAutoRecordCalls(enabled)
    }

    fun updateUserSettings(context: Context, transform: (UserAppSettings) -> UserAppSettings) =
        viewModelScope.launch(Dispatchers.IO) {
            AppSettingsManager(context).updateSettings(transform)
        }

    fun loadBlockedNumbers(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val dao = AetherDatabase.getDatabase(context).blockedNumberDao()
            dao.getAllBlocked().collect { list ->
                blockedList.value = list
            }
        }
    }

    fun addBlockedNumber(context: Context, number: String, label: String = "Spam / Telemarketer") {
        blockAndReportSpam(context, number, label, isSpam = true)
    }

    fun blockAndReportSpam(
        context: Context,
        number: String,
        reason: String = "Spam / Telemarketer",
        isSpam: Boolean = true,
    ) {
        if (number.isBlank()) return
        val normalizedNumber = PhoneNormalizer.normalizeDigits(number)
        val cleanNumber = normalizedNumber.ifBlank { number.trim() }

        updateUserSettings(context) { s ->
            s.copy(blockedNumbers = (s.blockedNumbers + cleanNumber).toSet())
        }

        viewModelScope.launch(Dispatchers.IO) {
            val dao = AetherDatabase.getDatabase(context).blockedNumberDao()
            dao.blockNumber(
                BlockedNumberEntity(
                    phoneNumber = cleanNumber,
                    label = reason,
                    isSpam = isSpam,
                    blockedAtTimestamp = System.currentTimeMillis(),
                ),
            )
            try {
                val values = ContentValues().apply {
                    put(BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER, cleanNumber)
                }
                context.contentResolver.insert(BlockedNumberContract.BlockedNumbers.CONTENT_URI, values)
            } catch (_: Exception) {
            }
            withContext(Dispatchers.Main) {
                context.showToast("Blocked & reported $cleanNumber as $reason")
            }
        }
    }

    fun removeBlockedNumber(context: Context, number: String) {
        if (number.isBlank()) return
        val normalizedNumber = number.replace("\\s+".toRegex(), "").replace("-", "")
        updateUserSettings(context) { s -> s.copy(blockedNumbers = s.blockedNumbers - normalizedNumber) }

        viewModelScope.launch(Dispatchers.IO) {
            val dao = AetherDatabase.getDatabase(context).blockedNumberDao()
            val existing = dao.getBlockedEntry(normalizedNumber)
            if (existing != null) {
                dao.unblockNumber(existing)
            }
            try {
                context.contentResolver.delete(
                    BlockedNumberContract.BlockedNumbers.CONTENT_URI,
                    "${BlockedNumberContract.BlockedNumbers.COLUMN_ORIGINAL_NUMBER} = ?",
                    arrayOf(normalizedNumber),
                )
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }
    }

    fun toggleBlockContact(context: Context, number: String, label: String? = null) {
        if (number.isBlank()) return
        val normalizedNumber = number.replace("\\s+".toRegex(), "").replace("-", "")

        viewModelScope.launch(Dispatchers.IO) {
            val dao = AetherDatabase.getDatabase(context).blockedNumberDao()
            val existing = dao.getBlockedEntry(normalizedNumber)
            if (existing != null) {
                removeBlockedNumber(context, normalizedNumber)
            } else {
                addBlockedNumber(context, normalizedNumber)
            }
        }
    }

    fun checkIsNumberBlocked(context: Context, number: String): Flow<Boolean> = flow {
        if (number.isBlank()) {
            emit(false)
            return@flow
        }
        val normalizedNumber = number.replace("\\s+".toRegex(), "").replace("-", "")
        val dao = AetherDatabase.getDatabase(context).blockedNumberDao()
        emit(dao.isBlocked(normalizedNumber))
    }

    fun observeStarredContacts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            ContactsRepository.observeStarredContacts(context).collect { list ->
                starredContacts.value = list
            }
        }
    }

    fun toggleFavorite(
        context: Context,
        contactId: Long,
        currentStarred: Boolean,
        fallbackNumber: String = "",
    ) {
        val newStarred = !currentStarred
        val normFallback = PhoneNormalizer.normalizeDigits(fallbackNumber)

        // 1. Update in-memory flows immediately
        val updatedList = contactsList.value.map { item ->
            val matchesId = contactId > 0L && item.id == contactId
            val matchesPhone = normFallback.isNotEmpty() &&
                (PhoneNormalizer.normalizeDigits(item.primaryNumber) == normFallback ||
                    item.phones.any { PhoneNormalizer.normalizeDigits(it.value) == normFallback })

            if (matchesId || matchesPhone) {
                item.copy(isStarred = newStarred)
            } else {
                item
            }
        }
        contactsList.value = updatedList
        starredContacts.value = updatedList.filter { it.isStarred }

        // 2. Persist in SharedPreferences + ContactsContract and refresh with overrides
        viewModelScope.launch(Dispatchers.IO) {
            ContactsRepository.toggleStarContact(
                context = context,
                contactId = contactId,
                star = newStarred,
                fallbackNumber = fallbackNumber,
            )
            val fresh = ContactsRepository.applyLocalOverrides(context, ContactsRepository.getContacts(context))
            contactsList.value = fresh
            starredContacts.value = fresh.filter { it.isStarred }
            try {
                com.aether.call.widget.FavoritesWidgetReceiver.updateAllWidgets(context)
            } catch (_: Exception) {
            }
        }
    }

    fun toggleFavorite(context: Context, contactIdString: String, currentStarred: Boolean) {
        val idLong = contactIdString.toLongOrNull() ?: 0L
        toggleFavorite(context, idLong, currentStarred)
    }

    fun toggleStar(context: Context, contact: ContactItem) {
        toggleFavorite(
            context = context,
            contactId = contact.id,
            currentStarred = contact.isStarred,
            fallbackNumber = contact.primaryNumber,
        )
    }

    fun openContactCard(contact: ContactItem, context: Context? = null) {
        // Resolve the richest version from contactsList immediately if available
        val resolved = if (contact.id > 0L) {
            contactsList.value.find { it.id == contact.id } ?: contact
        } else {
            contact
        }
        activeContactCard.value = resolved

        val contactNorms = resolved.phones
            .map { PhoneNormalizer.normalizeDigits(it.value) }
            .filter { it.isNotEmpty() }
            .toSet()

        activeContactHistory.value = _recentCalls.value.filter { record ->
            val recNorm = PhoneNormalizer.normalizeDigits(record.number)
            (recNorm.isNotEmpty() && contactNorms.contains(recNorm)) ||
                    (record.name != null && record.name.equals(resolved.displayName, ignoreCase = true))
        }

        if (context != null) {
            if (resolved.primaryNumber.isNotBlank()) {
                loadContactPreferences(context, resolved.primaryNumber)
            }
            if (resolved.id > 0L) {
                loadFullDetailedContact(context, resolved.id)
            }
        }
    }

    private fun loadFullDetailedContact(context: Context, contactId: Long) {
        if (contactId <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            val detailed = ContactsRepository.getContactDetails(context, contactId.toString())
            if (detailed.displayName.isNotBlank() || detailed.phones.isNotEmpty()) {
                activeContactCard.value = detailed
            }
        }
    }

    private fun loadContactPreferences(context: Context, number: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AetherDatabase.getDatabase(context)
            val noteEntity = db.contactNoteDao().getNoteForNumber(number)
            activeContactNote.value = noteEntity?.note ?: ""
            activeContactAncLevel.value = noteEntity?.ancLevel ?: 0.8f
            activeContactPreferredSim.value = noteEntity?.preferredSim ?: 1
        }
    }

    fun closeContactCard() {
        activeContactCard.value = null
        activeContactHistory.value = emptyList()
        activeContactNote.value = ""
        isEditingContact.value = false
    }

    fun saveContactNote(context: Context, number: String, noteText: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AetherDatabase.getDatabase(context)
            val existing = db.contactNoteDao().getNoteForNumber(number)
            val updated = (existing ?: ContactNoteEntity(phoneNumber = number)).copy(note = noteText)
            db.contactNoteDao().saveNote(updated)
            activeContactNote.value = noteText
        }
    }

    fun setContactAncLevel(context: Context, number: String, ancLevel: Float) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AetherDatabase.getDatabase(context)
            val existing = db.contactNoteDao().getNoteForNumber(number)
            val updated = (existing ?: ContactNoteEntity(phoneNumber = number)).copy(ancLevel = ancLevel)
            db.contactNoteDao().saveNote(updated)
            activeContactAncLevel.value = ancLevel
        }
    }

    fun setContactPreferredSim(context: Context, number: String, simSlot: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val db = AetherDatabase.getDatabase(context)
            val existing = db.contactNoteDao().getNoteForNumber(number)
            val updated = (existing ?: ContactNoteEntity(phoneNumber = number)).copy(preferredSim = simSlot)
            db.contactNoteDao().saveNote(updated)
            activeContactPreferredSim.value = simSlot
        }
    }

    fun toggleEditMode(enabled: Boolean) {
        isEditingContact.value = enabled
    }

    fun saveContactChanges(
        context: Context,
        contactId: Long,
        updatedName: String,
        updatedPhone: String,
        updatedEmail: String?,
        updatedOrg: String?,
        updatedTitle: String?,
        updatedNote: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = ContactsRepository.updateFullContact(
                context = context,
                contactId = contactId,
                updatedName = updatedName,
                updatedPhone = updatedPhone,
                updatedEmail = updatedEmail,
                updatedOrg = updatedOrg,
                updatedTitle = updatedTitle,
                updatedNote = updatedNote,
            )
            if (success) {
                if (!updatedNote.isNullOrBlank()) {
                    saveContactNote(context, updatedPhone, updatedNote)
                }
                refreshAllDataSync(context)
                isEditingContact.value = false
            }
        }
    }

    fun saveContactChanges(
        context: Context,
        contactIdString: String,
        updatedName: String,
        updatedPhone: String,
        updatedEmail: String?,
        updatedOrg: String?,
        updatedTitle: String?,
        updatedNote: String?,
    ) {
        val idLong = contactIdString.toLongOrNull() ?: 0L
        saveContactChanges(context, idLong, updatedName, updatedPhone, updatedEmail, updatedOrg, updatedTitle, updatedNote)
    }

    fun loadAvailableAccounts(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val accounts = ContactsRepository.getAvailableAccounts(context)
            availableAccounts.value = accounts
            selectedAccount.value = accounts.firstOrNull { it.type == "com.google" } ?: accounts.firstOrNull()
        }
    }

    fun selectAccount(account: ContactAccount) {
        selectedAccount.value = account
    }

    fun saveContact(
        context: Context,
        name: String,
        phone: String,
        email: String?,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val success = ContactsRepository.createContact(
                context = context,
                name = name,
                phone = phone,
                email = email,
            )
            if (success) {
                refreshAllDataSync(context)
                closeContactSheet()
            }
        }
    }

    fun createNewContact(context: Context, name: String, number: String) {
        saveContact(context, name, number, "")
    }

    /**
     * Saves a complete multi-field Samsung One UI contact (structured name, multiple phones,
     * emails, addresses, dates, relationships, websites, avatar, and calling card poster)
     * or updates My Profile if id == -1L.
     */
    fun saveFullContact(context: Context, contactItem: ContactItem) {
        if (contactItem.id == -1L || isEditingMyProfile.value) {
            updateMyProfile(context, contactItem)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            if (contactItem.id > 0L) {
                // Update existing contact cleanly by replacing Data rows via batch operations
                ContactsRepository.deleteContact(context, contactItem.id)
                ContactsRepository.insertFullContact(context, contactItem.copy(id = 0L))
            } else {
                ContactsRepository.insertFullContact(context, contactItem)
            }

            if (!contactItem.callingCardPhotoUri.isNullOrBlank() && contactItem.primaryNumber.isNotBlank()) {
                ContactsRepository.saveCustomContactPhoto(
                    context,
                    contactItem.primaryNumber,
                    contactItem.callingCardPhotoUri
                )
            }
            if (contactItem.note.isNotBlank() && contactItem.primaryNumber.isNotBlank()) {
                saveContactNote(context, contactItem.primaryNumber, contactItem.note)
            }

            refreshAllDataSync(context)

            // Re-select newly saved contact by normalized phone so Contact Card updates live
            val primaryNorm = PhoneNormalizer.normalizeDigits(contactItem.primaryNumber)
            val savedMatch = contactsList.value.find { c ->
                c.phones.any { PhoneNormalizer.normalizeDigits(it.value) == primaryNorm }
            }
            if (savedMatch != null) {
                activeContactCard.value = savedMatch.copy(
                    callingCardPhotoUri = contactItem.callingCardPhotoUri ?: savedMatch.callingCardPhotoUri,
                    photoUri = contactItem.photoUri ?: savedMatch.photoUri
                )
            }
            closeContactSheet()
        }
    }

    fun updateContact(context: Context, contactId: Long, name: String, number: String) {
        saveContactChanges(context, contactId, name, number, "", "", "", "")
    }

    fun updateContact(context: Context, contactIdString: String, name: String, number: String) {
        val idLong = contactIdString.toLongOrNull() ?: 0L
        updateContact(context, idLong, name, number)
    }

    fun openCreateContact(initialNumber: String = "") {
        selectedContactForEdit.value = if (initialNumber.isNotBlank()) {
            ContactItem(
                id = 0L,
                displayName = "",
                phones = listOf(LabeledValue(initialNumber, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, "Mobile"))
            )
        } else {
            null
        }
        isEditingMyProfile.value = false
        isCreateContactOpen.value = true
    }

    fun openEditContact(contact: ContactItem) {
        selectedContactForEdit.value = contact
        isEditingMyProfile.value = (contact.id == -1L)
        isCreateContactOpen.value = true
    }

    fun closeContactSheet() {
        isCreateContactOpen.value = false
        isEditingMyProfile.value = false
        selectedContactForEdit.value = null
        activeDetailedContact.value = null
        isEditingContact.value = false
    }

    fun loadContacts(context: Context) {
        startLiveSyncObservers(context)
        viewModelScope.launch(Dispatchers.IO) {
            isLoadingContacts.value = true
            val loaded = ContactsRepository.loadAllContacts(context)
            contactsList.value = loaded
            starredContacts.value = loaded.filter { it.isStarred }
            isLoadingContacts.value = false

            try {
                val searchDao = AetherDatabase.getDatabase(context).contactSearchDao()
                val ftsEntities = loaded.mapIndexed { index, item ->
                    ContactFtsEntity(
                        rowid = index + 1,
                        contactId = item.id.toString(),
                        displayName = item.displayName,
                        phoneNumber = item.primaryNumber,
                        organization = "",
                        notes = item.note,
                    )
                }
                searchDao.clearIndex()
                searchDao.insertAll(ftsEntities)
            } catch (_: Exception) {
            }
        }
    }

    fun setCustomContactPhoto(context: Context, phoneNumber: String, photoUri: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ContactsRepository.saveCustomContactPhoto(context, phoneNumber, photoUri)
            refreshAllDataSync(context)
        }
    }

    val isLoadingRecents = MutableStateFlow(true)

    fun loadRecentCalls(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000L)

            // Stage 1: Fast load of past 7 days history (< 5ms)
            val firstWeekCalls = CallLogRepository.getRecentCallsFirstWeek(context, oneWeekAgo)
            if (firstWeekCalls.isNotEmpty()) {
                _recentCalls.value = firstWeekCalls
                isLoadingRecents.value = false
            }

            // Stage 2: Background load of older call history (older than 1 week)
            val olderCalls = CallLogRepository.getOlderCallRecords(context, oneWeekAgo)
            if (olderCalls.isNotEmpty()) {
                _recentCalls.value = if (firstWeekCalls.isNotEmpty()) {
                    firstWeekCalls + olderCalls
                } else {
                    olderCalls
                }
            } else if (firstWeekCalls.isEmpty()) {
                _recentCalls.value = emptyList()
            }
            isLoadingRecents.value = false
        }
    }

    fun loadActiveSims(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val sims = SimManager.getActiveSims(context)
            activeSims.value = sims
            activeSimList.value = sims
        }
    }

    fun placeOutgoingCall(
        context: Context,
        number: String = _enteredNumber.value,
        preferredSlot: Int? = null,
        isVideo: Boolean = false,
    ) {
        if (number.isBlank()) return
        _enteredNumber.value = ""

        val rawClean = number.replace("\\s+".toRegex(), "").replace("-", "")
        val targetNumber = if (userSettings.value.assistedDialingIndia && rawClean.length == 10 && rawClean.first() in '6'..'9') {
            "+91$rawClean"
        } else {
            number
        }

        viewModelScope.launch(Dispatchers.IO) {
            val sims = SimManager.getActiveSims(context)
            activeSims.value = sims
            activeSimList.value = sims

            val askEveryTime = com.aether.call.util.SimAccountResolver.shouldAskSimEveryTime(context)
            if (askEveryTime && sims.size > 1) {
                val targetNorm = PhoneNormalizer.normalizeDigits(targetNumber)
                val matchingContact = contactsList.value.find { c ->
                    c.phones.any { PhoneNormalizer.normalizeDigits(it.value) == targetNorm }
                }
                simSelectionDialog.value = CallTarget(
                    number = targetNumber,
                    contactName = matchingContact?.displayName,
                    isVideo = isVideo,
                )
                return@launch
            }

            val slotPref = if (!askEveryTime) {
                preferredSlot ?: AetherDatabase.getDatabase(context).simPreferenceDao().getPreferredSlot(targetNumber)
            } else {
                preferredSlot
            }
            val defaultSlot = if (!askEveryTime) userSettings.value.defaultSimSlot else -1

            if (sims.size <= 1) {
                val targetSim = sims.firstOrNull()
                val handle = targetSim?.phoneAccountHandle
                    ?: com.aether.call.util.SimAccountResolver.findPhoneAccountHandleForSlot(context, targetSim?.slotIndex ?: 0)
                if (isVideo) {
                    CallLogRepository.placeCarrierVideoCall(context, targetNumber, handle)
                } else {
                    CallLogRepository.placeCarrierCall(context, targetNumber, preferredAccountHandle = handle)
                }
            } else {
                val targetSlot = slotPref ?: if (defaultSlot in 0..1) defaultSlot else null
                val matchingSim = if (targetSlot != null) {
                    sims.find { it.slotIndex == targetSlot || it.slotIndex == (targetSlot - 1) }
                } else null

                if (matchingSim != null) {
                    val handle = matchingSim.phoneAccountHandle
                        ?: com.aether.call.util.SimAccountResolver.findPhoneAccountHandleForSlot(context, matchingSim.slotIndex)
                    val carrierName = matchingSim.carrierName.ifBlank { "SIM" }
                    com.aether.call.telecom.CallBridge.simLabel.value = "SIM ${matchingSim.slotIndex + 1} • $carrierName"
                    if (isVideo) {
                        CallLogRepository.placeCarrierVideoCall(context, targetNumber, handle)
                    } else {
                        CallLogRepository.placeCarrierCall(context, targetNumber, preferredAccountHandle = handle)
                    }
                } else {
                    val targetNorm = PhoneNormalizer.normalizeDigits(targetNumber)
                    val matchingContact = contactsList.value.find { c ->
                        c.phones.any { PhoneNormalizer.normalizeDigits(it.value) == targetNorm }
                    }
                    simSelectionDialog.value = CallTarget(
                        number = targetNumber,
                        contactName = matchingContact?.displayName,
                        isVideo = isVideo,
                    )
                }
            }
        }
    }

    fun selectSimAndMakeCall(
        context: Context,
        targetNumber: String,
        slotIndex: Int,
        isVideo: Boolean = simSelectionDialog.value?.isVideo == true,
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val prefDao = AetherDatabase.getDatabase(context).simPreferenceDao()
            prefDao.savePreferredSlot(
                SimPreferenceEntity(
                    phoneNumber = targetNumber,
                    preferredSlotIndex = slotIndex,
                ),
            )

            val sims = SimManager.getActiveSims(context)
            activeSims.value = sims
            activeSimList.value = sims
            val selectedSim = sims.find { it.slotIndex == slotIndex } ?: sims.firstOrNull()

            simSelectionDialog.value = null

            val handle = selectedSim?.phoneAccountHandle
                ?: com.aether.call.util.SimAccountResolver.findPhoneAccountHandleForSlot(context, slotIndex)
            val carrierName = selectedSim?.carrierName?.ifBlank { "SIM" } ?: "SIM"
            com.aether.call.telecom.CallBridge.simLabel.value = "SIM ${slotIndex + 1} • $carrierName"

            if (isVideo) {
                CallLogRepository.placeCarrierVideoCall(context, targetNumber, handle)
            } else {
                CallLogRepository.placeCarrierCall(context, targetNumber, preferredAccountHandle = handle)
            }
        }
    }

    fun dismissSimSelectionDialog() {
        simSelectionDialog.value = null
    }

    fun makeCall(context: Context, number: String = _enteredNumber.value) {
        placeOutgoingCall(context, number)
    }

    fun makeCallWithSimSlot(context: Context, number: String = _enteredNumber.value, simSlot: Int = 1) {
        placeOutgoingCall(context, number, preferredSlot = simSlot - 1)
    }

    fun editContact(context: Context, contact: ContactItem) {
        openEditContact(contact)
    }

    fun deleteContact(context: Context, contact: ContactItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (contact.id > 0L) {
                ContactsRepository.deleteContact(context, contact.id)
                refreshAllDataSync(context)
                closeContactCard()
            }
        }
    }

    fun openContactCardForNumber(context: Context, number: String) {
        if (number.isBlank()) return
        val targetNorm = PhoneNormalizer.normalizeDigits(number)

        viewModelScope.launch(Dispatchers.IO) {
            val matchingContact = contactsList.value.find { c ->
                c.phones.any { PhoneNormalizer.normalizeDigits(it.value) == targetNorm }
            } ?: ContactsRepository.findContactByNumber(context, number)

            if (matchingContact != null) {
                openContactCard(matchingContact, context)
            } else {
                val unknownContact = ContactItem(
                    id = 0L,
                    displayName = number,
                    phones = listOf(LabeledValue(number, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, "Mobile"))
                )
                openContactCard(unknownContact, context)
            }
        }
    }

    fun openCallDetails(logItem: CallLogItem) {
        val targetNorm = PhoneNormalizer.normalizeDigits(logItem.number)
        val matchingContact = contactsList.value.find { c ->
            c.phones.any { PhoneNormalizer.normalizeDigits(it.value) == targetNorm }
        } ?: ContactItem(
            id = 0L,
            displayName = logItem.name ?: logItem.number,
            phones = listOf(LabeledValue(logItem.number, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE, "Mobile"))
        )
        openContactCard(matchingContact)
    }

    fun clearAllCallLogs(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            loadRecentCalls(context)
        }
    }

    fun openDeleteLogsDialog() {
    }

    fun launchSms(context: Context, number: String) {
        try {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
        }
    }

    fun checkAndRequestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}"),
                ).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }
    }

    fun checkRoleStatus(context: Context) {
        val isDefault = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            @Suppress("DEPRECATION")
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            @Suppress("DEPRECATION")
            telecomManager?.defaultDialerPackage == context.packageName
        }
        isDefaultDialer.value = isDefault
        if (isDefault) {
            checkAndRequestOverlayPermission(context)
        }
    }

    fun checkDefaultDialerRole(context: Context) {
        checkRoleStatus(context)
    }
}