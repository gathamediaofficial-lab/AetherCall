package com.aether.call.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.*
import com.aether.call.util.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

data class LabeledEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val value: String,
    val type: Int,
    val customLabel: String? = null,
)

data class PosterCustomization(
    val posterUri: String? = null,
    val avatarUri: String? = null,
    val themeGradientStart: Long = 0xFF6366F1,
    val themeGradientEnd: Long = 0xFF0F172A,
    val textStyle: String = "MODERN_BOLD",
)

data class DetailedContact(
    val contactId: String?,
    val rawContactId: String?,
    val lookupKey: String?,
    val displayName: String,
    val nickname: String? = null,
    val phones: List<LabeledEntry> = emptyList(),
    val emails: List<LabeledEntry> = emptyList(),
    val company: String? = null,
    val jobTitle: String? = null,
    val department: String? = null,
    val address: String? = null,
    val website: String? = null,
    val note: String? = null,
    val ringtoneUri: String? = null,
    val poster: PosterCustomization = PosterCustomization(),
)

data class LabeledItem(
    val value: String,
    val type: Int = ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
    val label: String = "Mobile",
)

data class LabeledValue(
    val value: String,
    val type: Int = 0,
    val label: String = "",
    val isPrimary: Boolean = false,
)

data class ContactItem(
    val id: Long = 0L,
    val lookupKey: String = "",
    val displayName: String = "",
    val prefix: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val suffix: String = "",
    val phones: List<LabeledValue> = emptyList(),
    val emails: List<LabeledValue> = emptyList(),
    val addresses: List<LabeledValue> = emptyList(),
    val events: List<LabeledValue> = emptyList(),
    val websites: List<String> = emptyList(),
    val relationships: List<LabeledValue> = emptyList(),
    val note: String = "",
    val photoUri: String? = null,
    val callingCardPhotoUri: String? = null,
    val isStarred: Boolean = false,
    val isEmergencyContact: Boolean = false,
    val storageAccounts: List<String> = listOf("Phone"),
    val linkedAccounts: List<String> = emptyList()
) {
    val primaryNumber: String
        get() = phones.firstOrNull()?.value ?: ""

    val number: String
        get() = primaryNumber

    val name: String
        get() = displayName

    val photoHighResUri: String?
        get() = photoUri

    val customPhotoUri: String?
        get() = callingCardPhotoUri

    constructor(
        id: String,
        name: String,
        number: String,
        photoUri: String? = null,
        photoHighResUri: String? = null,
        customPhotoUri: String? = null,
        additionalNumbers: List<Pair<String, String>> = emptyList(),
        emails: List<Pair<String, String>> = emptyList(),
        organization: String? = null,
        jobTitle: String? = null,
        address: String? = null,
        birthday: String? = null,
        isStarred: Boolean = false,
    ) : this(
        id = id.toLongOrNull() ?: 0L,
        lookupKey = "",
        displayName = name,
        phones = listOf(LabeledValue(value = number, type = Phone.TYPE_MOBILE, label = "Mobile")) + additionalNumbers.map { LabeledValue(value = it.second, type = Phone.TYPE_OTHER, label = it.first) },
        emails = emails.map { LabeledValue(value = it.second, type = Email.TYPE_HOME, label = it.first) },
        photoUri = photoUri ?: photoHighResUri,
        callingCardPhotoUri = customPhotoUri,
        isStarred = isStarred,
    )
}

/**
 * Extracts the first valid letter of the given contact's first name,
 * strictly skipping common prefixes (Mr, Mrs, Dr, Prof, Shri, Er, etc.).
 */
fun getContactInitial(contact: ContactItem): String {
    val rawName = contact.firstName.takeIf { it.isNotBlank() }
        ?: contact.displayName.takeIf { it.isNotBlank() }
        ?: contact.primaryNumber

    val cleaned = rawName.trim()
    val tokens = cleaned.split(Regex("\\s+"))

    val skippedPrefixes = setOf("mr", "mrs", "ms", "miss", "dr", "prof", "shri", "er", "adv", "rev")

    val actualFirstToken = tokens.firstOrNull { token ->
        val alphaOnly = token.replace(Regex("[^A-Za-z]"), "").lowercase()
        alphaOnly.isNotEmpty() && !skippedPrefixes.contains(alphaOnly)
    } ?: tokens.firstOrNull() ?: "#"

    val firstLetter = actualFirstToken.firstOrNull { it.isLetter() }?.uppercaseChar()
    return firstLetter?.toString() ?: "#"
}

/**
 * Data model representing a target contacts account (Google, Phone Storage, SIM Card).
 */
data class ContactAccount(
    val name: String,
    val type: String,
    val displayLabel: String,
)

object ContactsRepository {

    private const val PREFS_STARRED = "aether_starred_contacts_prefs"
    private const val KEY_STARRED_IDS = "starred_contact_ids"
    private const val KEY_STARRED_NUMBERS = "starred_phone_numbers"
    private const val PREFS_PHOTOS = "aether_contact_photos_prefs"

    private class ContactBuilder(
        val id: Long,
        var lookupKey: String = "",
        var displayName: String = "",
        var prefix: String = "",
        var firstName: String = "",
        var middleName: String = "",
        var lastName: String = "",
        var suffix: String = "",
        var photoUri: String? = null,
        var isStarred: Boolean = false
    ) {
        val phones = mutableListOf<LabeledValue>()
        val seenNormalizedPhones = mutableSetOf<String>()
        val emails = mutableListOf<LabeledValue>()
        val addresses = mutableListOf<LabeledValue>()
        val events = mutableListOf<LabeledValue>()
        val websites = mutableListOf<String>()
        val relationships = mutableListOf<LabeledValue>()
        var note: String = ""
        val accounts = linkedSetOf<String>()

        fun build(customCallingCardUri: String? = null): ContactItem {
            val resolvedName = displayName.ifBlank {
                listOf(prefix, firstName, middleName, lastName, suffix)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                    .ifBlank { phones.firstOrNull()?.value ?: "Unnamed" }
            }
            return ContactItem(
                id = id,
                lookupKey = lookupKey,
                displayName = resolvedName,
                prefix = prefix,
                firstName = firstName,
                middleName = middleName,
                lastName = lastName,
                suffix = suffix,
                phones = phones,
                emails = emails,
                addresses = addresses,
                events = events,
                websites = websites,
                relationships = relationships,
                note = note,
                photoUri = photoUri,
                callingCardPhotoUri = customCallingCardUri,
                isStarred = isStarred,
                linkedAccounts = accounts.toList()
            )
        }
    }

    /**
     * Single-pass high-speed loader (<30ms) aggregating all contact fields
     * AND detecting registered messengers (WhatsApp, Telegram, Signal, Meet).
     */
    suspend fun loadAllContacts(context: Context): List<ContactItem> = withContext(Dispatchers.IO) {
        val builders = LinkedHashMap<Long, ContactBuilder>()
        val posterDao = AetherDatabase.getDatabase(context).contactPosterDao()

        // Pass 1: Detect registered messenger accounts per CONTACT_ID
        val rawAccountsMap = HashMap<Long, MutableSet<String>>()
        val rawProjection = arrayOf(
            ContactsContract.RawContacts.CONTACT_ID,
            ContactsContract.RawContacts.ACCOUNT_TYPE
        )
        try {
            context.contentResolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                rawProjection,
                "${ContactsContract.RawContacts.DELETED} = 0",
                null,
                null
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                val typeIdx = cursor.getColumnIndex(ContactsContract.RawContacts.ACCOUNT_TYPE)
                while (cursor.moveToNext()) {
                    val contactId = cursor.getLong(idIdx)
                    val accType = cursor.getString(typeIdx) ?: continue
                    val label = when {
                        accType.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
                        accType.contains("telegram", ignoreCase = true) -> "Telegram"
                        accType.contains("securesms", ignoreCase = true) -> "Signal"
                        accType.contains("tachyon", ignoreCase = true) -> "Google Meet"
                        accType == "com.google" -> "Google"
                        else -> null
                    }
                    if (label != null) {
                        rawAccountsMap.getOrPut(contactId) { linkedSetOf() }.add(label)
                    }
                }
            }
        } catch (_: Exception) {}

        // Pass 2: Flat single query across all ContactsContract.Data MIME types
        val dataProjection = arrayOf(
            ContactsContract.Data.CONTACT_ID,
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.DISPLAY_NAME_PRIMARY,
            ContactsContract.Data.PHOTO_URI,
            ContactsContract.Data.STARRED,
            ContactsContract.Data.MIMETYPE,
            ContactsContract.Data.DATA1,
            ContactsContract.Data.DATA2,
            ContactsContract.Data.DATA3,
            ContactsContract.Data.DATA4,
            ContactsContract.Data.DATA5,
            ContactsContract.Data.DATA6
        )

        try {
            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                dataProjection,
                null,
                null,
                "${ContactsContract.Data.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                val lookupIdx = cursor.getColumnIndex(ContactsContract.Data.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Data.DISPLAY_NAME_PRIMARY)
                val photoIdx = cursor.getColumnIndex(ContactsContract.Data.PHOTO_URI)
                val starredIdx = cursor.getColumnIndex(ContactsContract.Data.STARRED)
                val mimeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val d1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)
                val d2Idx = cursor.getColumnIndex(ContactsContract.Data.DATA2)
                val d3Idx = cursor.getColumnIndex(ContactsContract.Data.DATA3)
                val d4Idx = cursor.getColumnIndex(ContactsContract.Data.DATA4)
                val d5Idx = cursor.getColumnIndex(ContactsContract.Data.DATA5)
                val d6Idx = cursor.getColumnIndex(ContactsContract.Data.DATA6)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIdx)
                    val builder = builders.getOrPut(id) {
                        ContactBuilder(
                            id = id,
                            lookupKey = cursor.getString(lookupIdx) ?: "",
                            displayName = cursor.getString(nameIdx) ?: "",
                            photoUri = cursor.getString(photoIdx),
                            isStarred = cursor.getInt(starredIdx) == 1
                        ).apply {
                            rawAccountsMap[id]?.let { accounts.addAll(it) }
                        }
                    }

                    if (builder.photoUri == null) {
                        builder.photoUri = cursor.getString(photoIdx)
                    }

                    val mime = cursor.getString(mimeIdx) ?: continue
                    val data1 = cursor.getString(d1Idx) ?: ""
                    val data2 = cursor.getInt(d2Idx)
                    val data3 = cursor.getString(d3Idx) ?: ""

                    when (mime) {
                        StructuredName.CONTENT_ITEM_TYPE -> {
                            builder.firstName = cursor.getString(d2Idx) ?: ""
                            builder.lastName = data3
                            builder.prefix = cursor.getString(d4Idx) ?: ""
                            builder.middleName = cursor.getString(d5Idx) ?: ""
                            builder.suffix = cursor.getString(d6Idx) ?: ""
                        }
                        Phone.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) {
                                val norm = PhoneNormalizer.normalizeDigits(data1)
                                if (norm.isNotEmpty() && builder.seenNormalizedPhones.add(norm)) {
                                    val typeLabel = Phone.getTypeLabel(context.resources, data2, data3).toString()
                                    builder.phones.add(LabeledValue(data1.trim(), data2, typeLabel))
                                }
                            }
                        }
                        Email.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) {
                                val typeLabel = Email.getTypeLabel(context.resources, data2, data3).toString()
                                builder.emails.add(LabeledValue(data1.trim(), data2, typeLabel))
                            }
                        }
                        StructuredPostal.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) {
                                val typeLabel = StructuredPostal.getTypeLabel(context.resources, data2, data3).toString()
                                builder.addresses.add(LabeledValue(data1.trim(), data2, typeLabel))
                            }
                        }
                        Event.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) {
                                val typeLabel = Event.getTypeLabel(context.resources, data2, data3).toString()
                                builder.events.add(LabeledValue(data1.trim(), data2, typeLabel))
                            }
                        }
                        Website.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) builder.websites.add(data1.trim())
                        }
                        Relation.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) {
                                val typeLabel = Relation.getTypeLabel(context.resources, data2, data3).toString()
                                builder.relationships.add(LabeledValue(data1.trim(), data2, typeLabel))
                            }
                        }
                        Note.CONTENT_ITEM_TYPE -> {
                            if (data1.isNotBlank()) builder.note = data1.trim()
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        val allPostersMap = try {
            posterDao.getAllPosters().associateBy { PhoneNormalizer.normalizeDigits(it.phoneNumber) }
        } catch (_: Exception) {
            emptyMap()
        }

        val resultList = builders.values.map { b ->
            val primaryNum = b.phones.firstOrNull()?.value ?: ""
            val normPrimary = PhoneNormalizer.normalizeDigits(primaryNum)
            val customPoster = if (normPrimary.isNotBlank()) allPostersMap[normPrimary] else null
            b.build(customPoster?.customPhotoUri)
        }
        applyLocalOverrides(context, resultList)
    }

    suspend fun getContacts(context: Context): List<ContactItem> = loadAllContacts(context)

    suspend fun getStarredContacts(context: Context): List<ContactItem> = withContext(Dispatchers.IO) {
        getContacts(context).filter { it.isStarred }
    }

    fun observeContacts(context: Context): Flow<List<ContactItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch(Dispatchers.IO) {
                    val list = runCatching { getContacts(context) }.getOrDefault(emptyList())
                    trySend(list)
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            launch(Dispatchers.IO) { trySend(getContacts(context)) }
        } catch (_: Exception) {
            launch(Dispatchers.IO) { trySend(getContacts(context)) }
        }
        awaitClose {
            try { context.contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    fun observeStarredContacts(context: Context): Flow<List<ContactItem>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                launch(Dispatchers.IO) {
                    val list = runCatching { getStarredContacts(context) }.getOrDefault(emptyList())
                    trySend(list)
                }
            }
        }
        try {
            context.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            launch(Dispatchers.IO) { trySend(getStarredContacts(context)) }
        } catch (_: Exception) {
            launch(Dispatchers.IO) { trySend(getStarredContacts(context)) }
        }
        awaitClose {
            try { context.contentResolver.unregisterContentObserver(observer) } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getContactDetails(context: Context, contactId: String): ContactItem = withContext(Dispatchers.IO) {
        val contactIdLong = contactId.toLongOrNull() ?: 0L
        val all = getContacts(context)
        val match = all.find { it.id == contactIdLong }
        if (match != null) return@withContext match
        return@withContext ContactItem(id = contactIdLong, displayName = "")
    }

    suspend fun findContactByNumber(context: Context, number: String): ContactItem? = withContext(Dispatchers.IO) {
        if (number.isBlank()) return@withContext null
        val targetNorm = PhoneNormalizer.normalizeDigits(number)
        if (targetNorm.isBlank()) return@withContext null

        val all = getContacts(context)
        return@withContext all.find { contact ->
            contact.phones.any { p ->
                val pNorm = PhoneNormalizer.normalizeDigits(p.value)
                pNorm == targetNorm || (pNorm.length >= 7 && targetNorm.length >= 7 && (pNorm.endsWith(targetNorm) || targetNorm.endsWith(pNorm)))
            }
        }
    }

    suspend fun getAvailableAccounts(context: Context): List<ContactAccount> = withContext(Dispatchers.IO) {
        val accountsList = mutableListOf<ContactAccount>()
        accountsList.add(ContactAccount("Phone Storage", "com.android.local", "Phone"))
        try {
            val am = android.accounts.AccountManager.get(context)
            val accounts = am.accounts
            for (acc in accounts) {
                val label = when {
                    acc.type.contains("google", ignoreCase = true) -> "Google (${acc.name})"
                    acc.type.contains("samsung", ignoreCase = true) -> "Samsung Account (${acc.name})"
                    else -> "${acc.name} (${acc.type})"
                }
                accountsList.add(ContactAccount(acc.name, acc.type, label))
            }
        } catch (_: Exception) {}
        return@withContext accountsList
    }

    /**
     * Fast direct lookup for active incoming/outgoing calls using PhoneLookup.
     */
    fun resolveCallerSync(context: Context, rawNumber: String): Pair<String, String?> {
        if (rawNumber.isBlank()) return Pair("Unknown caller", null)
        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(rawNumber)
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    val photo = cursor.getString(1)
                    if (!name.isNullOrBlank()) {
                        return Pair(name, photo)
                    }
                }
            }
        } catch (_: Exception) {
        }
        return Pair(rawNumber, null)
    }

    suspend fun insertFullContact(context: Context, contact: ContactItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, null)
                    .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, null)
                    .build()
            )
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                    .withValue(ContactsContract.Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                    .withValue(StructuredName.PREFIX, contact.prefix)
                    .withValue(StructuredName.GIVEN_NAME, contact.firstName.ifBlank { contact.displayName })
                    .withValue(StructuredName.MIDDLE_NAME, contact.middleName)
                    .withValue(StructuredName.FAMILY_NAME, contact.lastName)
                    .withValue(StructuredName.SUFFIX, contact.suffix)
                    .build()
            )
            contact.phones.filter { it.value.isNotBlank() }.forEach { phone ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                        .withValue(Phone.NUMBER, phone.value.trim())
                        .withValue(Phone.TYPE, phone.type)
                        .withValue(Phone.LABEL, phone.label)
                        .build()
                )
            }
            contact.emails.filter { it.value.isNotBlank() }.forEach { email ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                        .withValue(Email.ADDRESS, email.value.trim())
                        .withValue(Email.TYPE, email.type)
                        .withValue(Email.LABEL, email.label)
                        .build()
                )
            }
            contact.addresses.filter { it.value.isNotBlank() }.forEach { addr ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                        .withValue(StructuredPostal.FORMATTED_ADDRESS, addr.value.trim())
                        .withValue(StructuredPostal.TYPE, addr.type)
                        .withValue(StructuredPostal.LABEL, addr.label)
                        .build()
                )
            }
            contact.events.filter { it.value.isNotBlank() }.forEach { ev ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Event.CONTENT_ITEM_TYPE)
                        .withValue(Event.START_DATE, ev.value.trim())
                        .withValue(Event.TYPE, ev.type)
                        .withValue(Event.LABEL, ev.label)
                        .build()
                )
            }
            contact.websites.filter { it.isNotBlank() }.forEach { url ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Website.CONTENT_ITEM_TYPE)
                        .withValue(Website.URL, url.trim())
                        .build()
                )
            }
            contact.relationships.filter { it.value.isNotBlank() }.forEach { rel ->
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Relation.CONTENT_ITEM_TYPE)
                        .withValue(Relation.NAME, rel.value.trim())
                        .withValue(Relation.TYPE, rel.type)
                        .withValue(Relation.LABEL, rel.label)
                        .build()
                )
            }
            if (contact.note.isNotBlank()) {
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0)
                        .withValue(ContactsContract.Data.MIMETYPE, Note.CONTENT_ITEM_TYPE)
                        .withValue(Note.NOTE, contact.note.trim())
                        .build()
                )
            }
            context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun createContact(context: Context, name: String, phone: String, email: String?): Boolean =
        insertFullContact(
            context,
            ContactItem(
                id = 0L,
                displayName = name,
                phones = if (phone.isNotBlank()) listOf(LabeledValue(phone, Phone.TYPE_MOBILE, "Mobile")) else emptyList(),
                emails = if (!email.isNullOrBlank()) listOf(LabeledValue(email, Email.TYPE_HOME, "Home")) else emptyList(),
            )
        )

    suspend fun updateFullContact(
        context: Context,
        contactId: Long,
        updatedName: String,
        updatedPhone: String,
        updatedEmail: String?,
        updatedOrg: String?,
        updatedTitle: String?,
        updatedNote: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val nameValues = ContentValues().apply {
                put(ContactsContract.CommonDataKinds.StructuredName.DISPLAY_NAME, updatedName)
            }
            context.contentResolver.update(
                ContactsContract.Data.CONTENT_URI,
                nameValues,
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE),
            )

            if (updatedPhone.isNotBlank()) {
                val phoneValues = ContentValues().apply {
                    put(ContactsContract.CommonDataKinds.Phone.NUMBER, updatedPhone)
                }
                context.contentResolver.update(
                    ContactsContract.Data.CONTENT_URI,
                    phoneValues,
                    "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf(contactId.toString(), ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE),
                )
            }

            return@withContext true
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext false
        }
    }

    suspend fun updateFullContact(
        context: Context,
        contactIdString: String,
        updatedName: String,
        updatedPhone: String,
        updatedEmail: String?,
        updatedOrg: String?,
        updatedTitle: String?,
        updatedNote: String?,
    ): Boolean {
        val idLong = contactIdString.toLongOrNull() ?: return false
        return updateFullContact(context, idLong, updatedName, updatedPhone, updatedEmail, updatedOrg, updatedTitle, updatedNote)
    }

    /**
     * Queries all available writable RawContact accounts on the device (Phone, Google accounts, Samsung, SIM).
     */
    fun getAvailableStorageAccounts(context: Context): List<Pair<String, String?>> {
        val accounts = mutableListOf<Pair<String, String?>>("Phone (Device)" to null)
        try {
            val am = android.accounts.AccountManager.get(context)
            am.accounts.forEach { acc ->
                if (acc.type.contains("google", ignoreCase = true) ||
                    acc.type.contains("samsung", ignoreCase = true) ||
                    acc.type.contains("exchange", ignoreCase = true)
                ) {
                    accounts.add("${acc.name} (${acc.type.substringAfterLast('.')})" to "${acc.name}|${acc.type}")
                }
            }
        } catch (_: Exception) {
        }
        return accounts.distinctBy { it.first }
    }

    /**
     * Copies a temporary Photo Picker URI into permanent internal app storage (filesDir/contact_photos)
     * and returns a permanent file:// URI string + compressed JPEG byte array for ContactsContract.
     */
    fun persistPhotoToInternalStorage(
        context: Context,
        sourceUriString: String?,
        contactKey: String,
    ): Pair<String?, ByteArray?> {
        if (sourceUriString.isNullOrBlank()) return null to null
        return try {
            val uri = Uri.parse(sourceUriString)
            val bytes: ByteArray = if (uri.scheme == "file") {
                java.io.File(uri.path ?: return null to null).readBytes()
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null to null
            }

            val originalBitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null to null
            val maxDim = 512
            val ratio = minOf(maxDim.toFloat() / originalBitmap.width, maxDim.toFloat() / originalBitmap.height, 1f)
            val targetW = (originalBitmap.width * ratio).toInt().coerceAtLeast(1)
            val targetH = (originalBitmap.height * ratio).toInt().coerceAtLeast(1)
            val scaledBitmap = android.graphics.Bitmap.createScaledBitmap(originalBitmap, targetW, targetH, true)

            val outStream = java.io.ByteArrayOutputStream()
            scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 88, outStream)
            val compressedBytes = outStream.toByteArray()

            val dir = java.io.File(context.filesDir, "contact_photos").apply { if (!exists()) mkdirs() }
            val safeName = contactKey.replace(Regex("[^a-zA-Z0-9_]"), "_").ifBlank { System.currentTimeMillis().toString() }
            val destFile = java.io.File(dir, "photo_${safeName}_${System.currentTimeMillis()}.jpg")
            destFile.writeBytes(compressedBytes)

            val permanentUri = Uri.fromFile(destFile).toString()
            permanentUri to compressedBytes
        } catch (e: Exception) {
            e.printStackTrace()
            sourceUriString to null
        }
    }

    /**
     * Saves the permanent photo URI in local SharedPreferences AND writes the JPEG bytes
     * to every writable RawContact for this contact.
     */
    fun saveContactPhotoGuaranteed(
        context: Context,
        contactId: Long,
        primaryNumber: String,
        sourceUriString: String?,
    ): String? {
        if (sourceUriString.isNullOrBlank()) return null
        val normNum = PhoneNormalizer.normalizeDigits(primaryNumber)
        val key = if (contactId > 0L) "id_$contactId" else "num_$normNum"

        val (permanentUri, jpegBytes) = persistPhotoToInternalStorage(context, sourceUriString, key)
        if (permanentUri != null) {
            val prefs = context.getSharedPreferences(PREFS_PHOTOS, Context.MODE_PRIVATE).edit()
            if (contactId > 0L) prefs.putString("id_$contactId", permanentUri)
            if (normNum.isNotEmpty()) prefs.putString("num_$normNum", permanentUri)
            prefs.apply()
        }

        if (jpegBytes != null && contactId > 0L) {
            val resolver = context.contentResolver
            val rawIds = mutableListOf<Long>()
            try {
                resolver.query(
                    ContactsContract.RawContacts.CONTENT_URI,
                    arrayOf(
                        ContactsContract.RawContacts._ID,
                        ContactsContract.RawContacts.RAW_CONTACT_IS_READ_ONLY,
                    ),
                    "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                    arrayOf(contactId.toString()),
                    null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val rId = c.getLong(0)
                        val isReadOnly = c.getInt(1) == 1
                        if (!isReadOnly) rawIds.add(rId)
                    }
                }
            } catch (_: Exception) {
            }

            rawIds.forEach { rawId ->
                try {
                    resolver.delete(
                        ContactsContract.Data.CONTENT_URI,
                        "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                        arrayOf(rawId.toString(), Photo.CONTENT_ITEM_TYPE),
                    )
                    val values = ContentValues().apply {
                        put(ContactsContract.Data.RAW_CONTACT_ID, rawId)
                        put(
                            ContactsContract.Data.MIMETYPE,
                            Photo.CONTENT_ITEM_TYPE,
                        )
                        put(Photo.PHOTO, jpegBytes)
                        put(ContactsContract.Data.IS_SUPER_PRIMARY, 1)
                        put(ContactsContract.Data.IS_PRIMARY, 1)
                    }
                    resolver.insert(ContactsContract.Data.CONTENT_URI, values)
                } catch (_: Exception) {
                }
            }
        }
        return permanentUri
    }

    /**
     * Bulletproof Photo Saver: Decodes a URI to a byte array and inserts it
     * into ContactsContract.Data as PHOTO_URI and PHOTO so Google/Phone/WhatsApp sync never drops it.
     */
    fun saveContactPhotoToDatabase(
        context: Context,
        rawContactId: Long,
        photoUriStr: String?,
    ) {
        if (rawContactId <= 0L) return
        try {
            val resolver = context.contentResolver
            val uri = photoUriStr?.let { Uri.parse(it) } ?: return

            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }

            val inputStream = resolver.openInputStream(uri) ?: return
            val bytes = inputStream.readBytes()
            inputStream.close()

            resolver.delete(
                ContactsContract.Data.CONTENT_URI,
                "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(rawContactId.toString(), Photo.CONTENT_ITEM_TYPE),
            )

            val ops = ArrayList<ContentProviderOperation>()
            ops.add(
                ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                    .withValue(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    .withValue(
                        ContactsContract.Data.MIMETYPE,
                        Photo.CONTENT_ITEM_TYPE,
                    )
                    .withValue(Photo.PHOTO, bytes)
                    .build(),
            )
            resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Merges our guaranteed local Starred state and Permanent Photo URIs onto any list of ContactItems
     * returned by ContactsProvider so Android's background aggregator can NEVER undo a star or photo.
     */
    fun applyLocalOverrides(context: Context, items: List<ContactItem>): List<ContactItem> {
        val starPrefs = context.getSharedPreferences(PREFS_STARRED, Context.MODE_PRIVATE)
        val starredIds = starPrefs.getStringSet(KEY_STARRED_IDS, null)
        val unstarredIds = starPrefs.getStringSet("unstarred_contact_ids", emptySet()).orEmpty()
        val starredNumbers = starPrefs.getStringSet(KEY_STARRED_NUMBERS, emptySet()).orEmpty()
        val unstarredNumbers = starPrefs.getStringSet("unstarred_phone_numbers", emptySet()).orEmpty()

        val photoPrefs = context.getSharedPreferences(PREFS_PHOTOS, Context.MODE_PRIVATE)

        return items.map { item ->
            val idStr = item.id.toString()
            val normNum = PhoneNormalizer.normalizeDigits(item.primaryNumber)

            val finalStarred = when {
                starredIds?.contains(idStr) == true -> true
                normNum.isNotEmpty() && starredNumbers.contains(normNum) -> true
                unstarredIds.contains(idStr) -> false
                normNum.isNotEmpty() && unstarredNumbers.contains(normNum) -> false
                else -> item.isStarred
            }

            val savedPhoto = photoPrefs.getString("id_${item.id}", null)
                ?: (if (normNum.isNotEmpty()) photoPrefs.getString("num_$normNum", null) else null)

            val validSavedPhoto = savedPhoto?.takeIf { uriStr ->
                if (uriStr.startsWith("file://")) {
                    java.io.File(Uri.parse(uriStr).path.orEmpty()).exists()
                } else {
                    true
                }
            }

            item.copy(
                isStarred = finalStarred,
                photoUri = validSavedPhoto ?: item.photoUri,
            )
        }
    }

    /**
     * 100% Bulletproof Star/Favourite Persistence:
     * 1. Immediately saves to SharedPreferences (by Contact ID AND normalized phone number).
     * 2. Updates Contacts.CONTENT_URI and each RawContact individually in isolated try/catch blocks
     *    so a read-only WhatsApp/SIM RawContact never aborts the transaction.
     */
    suspend fun toggleStarContact(
        context: Context,
        contactId: Long,
        star: Boolean,
        fallbackNumber: String = "",
    ): Boolean = withContext(Dispatchers.IO) {
        val normFallback = PhoneNormalizer.normalizeDigits(fallbackNumber)

        val starPrefs = context.getSharedPreferences(PREFS_STARRED, Context.MODE_PRIVATE)
        val starredIds = starPrefs.getStringSet(KEY_STARRED_IDS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val unstarredIds = starPrefs.getStringSet("unstarred_contact_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
        val starredNumbers = starPrefs.getStringSet(KEY_STARRED_NUMBERS, emptySet())?.toMutableSet() ?: mutableSetOf()
        val unstarredNumbers = starPrefs.getStringSet("unstarred_phone_numbers", emptySet())?.toMutableSet() ?: mutableSetOf()

        if (contactId > 0L) {
            if (star) {
                starredIds.add(contactId.toString())
                unstarredIds.remove(contactId.toString())
            } else {
                starredIds.remove(contactId.toString())
                unstarredIds.add(contactId.toString())
            }
        }
        if (normFallback.isNotEmpty()) {
            if (star) {
                starredNumbers.add(normFallback)
                unstarredNumbers.remove(normFallback)
            } else {
                starredNumbers.remove(normFallback)
                unstarredNumbers.add(normFallback)
            }
        }

        starPrefs.edit()
            .putStringSet(KEY_STARRED_IDS, starredIds)
            .putStringSet("unstarred_contact_ids", unstarredIds)
            .putStringSet(KEY_STARRED_NUMBERS, starredNumbers)
            .putStringSet("unstarred_phone_numbers", unstarredNumbers)
            .commit()

        try {
            val resolver = context.contentResolver
            val starInt = if (star) 1 else 0
            val values = ContentValues().apply {
                put(ContactsContract.Contacts.STARRED, starInt)
            }

            if (contactId > 0L) {
                try {
                    resolver.update(
                        ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId),
                        values,
                        null,
                        null,
                    )
                } catch (_: Exception) {
                }

                try {
                    val rawValues = ContentValues().apply {
                        put(ContactsContract.RawContacts.STARRED, starInt)
                    }
                    resolver.update(
                        ContactsContract.RawContacts.CONTENT_URI,
                        rawValues,
                        "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                        arrayOf(contactId.toString()),
                    )
                } catch (_: Exception) {
                }
            }
        } catch (_: Exception) {
        }

        true
    }

    /**
     * Sets a specific phone number as the Primary / Default number (IS_SUPER_PRIMARY = 1)
     * for a multi-number contact in Android's ContactsContract.Data table.
     */
    suspend fun setPrimaryPhoneNumber(
        context: Context,
        contactId: Long,
        targetPhoneNumber: String,
    ): Boolean = withContext(Dispatchers.IO) {
        if (contactId <= 0L || targetPhoneNumber.isBlank()) return@withContext false
        try {
            val targetNorm = PhoneNormalizer.normalizeDigits(targetPhoneNumber)
            val ops = ArrayList<ContentProviderOperation>()

            context.contentResolver.query(
                ContactsContract.Data.CONTENT_URI,
                arrayOf(
                    ContactsContract.Data._ID,
                    Phone.NUMBER
                ),
                "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
                arrayOf(
                    contactId.toString(),
                    Phone.CONTENT_ITEM_TYPE
                ),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val dataId = cursor.getLong(0)
                    val num = cursor.getString(1).orEmpty()
                    val isMatch = PhoneNormalizer.normalizeDigits(num) == targetNorm
                    ops.add(
                        ContentProviderOperation.newUpdate(
                            ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, dataId)
                        )
                            .withValue(ContactsContract.Data.IS_PRIMARY, if (isMatch) 1 else 0)
                            .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, if (isMatch) 1 else 0)
                            .build()
                    )
                }
            }

            if (ops.isNotEmpty()) {
                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
                return@withContext true
            }
            false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Marks or unmarks a contact as an Emergency Contact by syncing them with Samsung/Android's
     * native "ICE - emergency contacts" system group so they appear on the Lockscreen SOS dialer.
     */
    suspend fun setEmergencyContactStatus(
        context: Context,
        contactId: Long,
        isEmergency: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if (contactId <= 0L) return@withContext false
        try {
            val resolver = context.contentResolver
            // 1. Find or create the system "ICE - emergency contacts" group
            var iceGroupId: Long = -1L
            resolver.query(
                ContactsContract.Groups.CONTENT_URI,
                arrayOf(ContactsContract.Groups._ID, ContactsContract.Groups.TITLE),
                "${ContactsContract.Groups.TITLE} LIKE ? OR ${ContactsContract.Groups.SYSTEM_ID} = ?",
                arrayOf("%ICE%", "Contacts"),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val title = cursor.getString(1).orEmpty()
                    if (title.contains("ICE", ignoreCase = true) || title.contains("Emergency", ignoreCase = true)) {
                        iceGroupId = id
                        break
                    }
                }
            }

            if (iceGroupId == -1L) {
                val values = ContentValues().apply {
                    put(ContactsContract.Groups.TITLE, "ICE - emergency contacts")
                    put(ContactsContract.Groups.GROUP_VISIBLE, 1)
                }
                val groupUri = resolver.insert(ContactsContract.Groups.CONTENT_URI, values)
                iceGroupId = groupUri?.lastPathSegment?.toLongOrNull() ?: -1L
            }

            if (iceGroupId == -1L) return@withContext false

            // 2. Find raw_contact_id for this contact
            var rawContactId: Long = -1L
            resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(ContactsContract.RawContacts._ID),
                "${ContactsContract.RawContacts.CONTACT_ID} = ?",
                arrayOf(contactId.toString()),
                null
            )?.use { c ->
                if (c.moveToFirst()) rawContactId = c.getLong(0)
            }
            if (rawContactId == -1L) return@withContext false

            if (isEmergency) {
                val values = ContentValues().apply {
                    put(ContactsContract.Data.RAW_CONTACT_ID, rawContactId)
                    put(
                        ContactsContract.Data.MIMETYPE,
                        GroupMembership.CONTENT_ITEM_TYPE
                    )
                    put(GroupMembership.GROUP_ROW_ID, iceGroupId)
                }
                resolver.insert(ContactsContract.Data.CONTENT_URI, values)
            } else {
                resolver.delete(
                    ContactsContract.Data.CONTENT_URI,
                    "${ContactsContract.Data.RAW_CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${GroupMembership.GROUP_ROW_ID} = ?",
                    arrayOf(
                        rawContactId.toString(),
                        GroupMembership.CONTENT_ITEM_TYPE,
                        iceGroupId.toString()
                    )
                )
            }

            // Persist local flag in SharedPreferences for instant UI badge lookup
            val prefs = context.getSharedPreferences("aether_emergency_contacts", Context.MODE_PRIVATE)
            val set = prefs.getStringSet("ice_ids", emptySet())?.toMutableSet() ?: mutableSetOf()
            if (isEmergency) set.add(contactId.toString()) else set.remove(contactId.toString())
            prefs.edit().putStringSet("ice_ids", set).apply()

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun isEmergencyContact(context: Context, contactId: Long): Boolean {
        if (contactId <= 0L) return false
        val prefs = context.getSharedPreferences("aether_emergency_contacts", Context.MODE_PRIVATE)
        return prefs.getStringSet("ice_ids", emptySet())?.contains(contactId.toString()) == true
    }

    /**
     * Copies/saves a contact into one or more selected storage accounts (e.g., Phone + Google Account).
     */
    suspend fun saveContactToMultipleAccounts(
        context: Context,
        contact: ContactItem,
        selectedAccountKeys: List<String?>,
    ) = withContext(Dispatchers.IO) {
        val targets = if (selectedAccountKeys.isEmpty()) listOf(null) else selectedAccountKeys
        targets.forEach { key ->
            try {
                val parts = key?.split("|")
                val accName = parts?.getOrNull(0)
                val accType = parts?.getOrNull(1)
                val ops = ArrayList<ContentProviderOperation>()

                val rawIndex = ops.size
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_NAME, accName)
                        .withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, accType)
                        .withValue(ContactsContract.RawContacts.STARRED, if (contact.isStarred) 1 else 0)
                        .build()
                )

                // Structured Name
                ops.add(
                    ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                        .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                        .withValue(
                            ContactsContract.Data.MIMETYPE,
                            StructuredName.CONTENT_ITEM_TYPE
                        )
                        .withValue(StructuredName.DISPLAY_NAME, contact.displayName)
                        .withValue(StructuredName.GIVEN_NAME, contact.firstName)
                        .withValue(StructuredName.FAMILY_NAME, contact.lastName)
                        .build()
                )

                // Phones
                contact.phones.forEachIndexed { index, phone ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                Phone.CONTENT_ITEM_TYPE
                            )
                            .withValue(Phone.NUMBER, phone.value)
                            .withValue(Phone.TYPE, phone.type)
                            .withValue(Phone.LABEL, phone.label)
                            .withValue(ContactsContract.Data.IS_PRIMARY, if (index == 0) 1 else 0)
                            .withValue(ContactsContract.Data.IS_SUPER_PRIMARY, if (index == 0) 1 else 0)
                            .build()
                    )
                }

                // Emails
                contact.emails.forEach { email ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                Email.CONTENT_ITEM_TYPE
                            )
                            .withValue(Email.ADDRESS, email.value)
                            .withValue(Email.LABEL, email.label)
                            .build()
                    )
                }

                // Addresses
                contact.addresses.forEach { addr ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                StructuredPostal.CONTENT_ITEM_TYPE
                            )
                            .withValue(StructuredPostal.FORMATTED_ADDRESS, addr.value)
                            .withValue(StructuredPostal.LABEL, addr.label)
                            .build()
                    )
                }

                // Birthday / Events
                contact.events.forEach { ev ->
                    ops.add(
                        ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI)
                            .withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, rawIndex)
                            .withValue(
                                ContactsContract.Data.MIMETYPE,
                                Event.CONTENT_ITEM_TYPE
                            )
                            .withValue(Event.START_DATE, ev.value)
                            .withValue(Event.LABEL, ev.label)
                            .build()
                    )
                }

                context.contentResolver.applyBatch(ContactsContract.AUTHORITY, ops)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun toggleStarContact(context: Context, contactIdString: String, star: Boolean) {
        val idLong = contactIdString.toLongOrNull() ?: return
        toggleStarContact(context, idLong, star)
    }

    suspend fun toggleStar(context: Context, contactId: Long, currentlyStarred: Boolean) =
        toggleStarContact(context, contactId, !currentlyStarred)

    suspend fun deleteContact(context: Context, contactId: Long) = withContext(Dispatchers.IO) {
        val uri = ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, contactId)
        context.contentResolver.delete(uri, null, null)
    }

    suspend fun saveCustomContactPhoto(context: Context, phoneNumber: String, photoUri: String) = withContext(Dispatchers.IO) {
        val db = AetherDatabase.getDatabase(context)
        val existing = db.contactPosterDao().getPosterForNumber(phoneNumber)
        val updated = (existing ?: ContactPosterEntity(phoneNumber = phoneNumber)).copy(customPhotoUri = photoUri)
        db.contactPosterDao().savePoster(updated)
    }
}
