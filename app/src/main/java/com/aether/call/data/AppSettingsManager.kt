package com.aether.call.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "aether_call_settings")

enum class IncomingCallLayoutStyle(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    SLIDE_TO_ANSWER(
        id = "SLIDE_TO_ANSWER",
        title = "Slide to answer (iOS style)",
        subtitle = "Minimalist screen with Message, Voicemail, and a horizontal slide track",
    ),
    DUAL_BUTTONS(
        id = "DUAL_BUTTONS",
        title = "Dual buttons (One UI style)",
        subtitle = "Center avatar radar with Quick reply pill and Red/Green action pods",
    );

    companion object {
        fun fromId(id: String?): IncomingCallLayoutStyle {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: SLIDE_TO_ANSWER
        }
    }
}

enum class AppThemeMode(
    val id: String,
    val title: String,
    val subtitle: String,
) {
    SYSTEM(
        id = "SYSTEM",
        title = "System default",
        subtitle = "Follow system theme settings",
    ),
    LIGHT(
        id = "LIGHT",
        title = "Light",
        subtitle = "Always use light theme",
    ),
    DARK(
        id = "DARK",
        title = "Dark",
        subtitle = "Always use dark theme",
    );

    companion object {
        fun fromId(id: String?): AppThemeMode {
            return entries.find { it.id.equals(id, ignoreCase = true) } ?: SYSTEM
        }
    }
}

/**
 * Settings configuration data model matching Samsung One UI Call Settings options.
 */
data class UserAppSettings(
    val appThemeMode: String = AppThemeMode.SYSTEM.id,
    val defaultSimSlot: Int = -1, // -1 = Always Ask, 0 = SIM 1, 1 = SIM 2
    val ancLevel: Float = 0.5f,
    val hapticsEnabled: Boolean = true,
    val hapticStrength: String = "MEDIUM",
    val flipToSilence: Boolean = true,
    val spamShieldEnabled: Boolean = true,
    val blockUnknownNumbers: Boolean = false,
    val blockedNumbers: Set<String> = emptySet(),
    val autoRecordCalls: Boolean = false,
    val showRecordingNotification: Boolean = true,
    val callBackgroundTheme: String = "GRAD_AUTO_AVATAR",
    val customCallBackgroundUri: String? = null,
    val incomingCallLayoutStyle: String = IncomingCallLayoutStyle.SLIDE_TO_ANSWER.id,
    val showCallerHistoryInCall: Boolean = true,
    val vibrateOnAnswer: Boolean = true,
    val vibrateOnEnd: Boolean = true,
    val playConnectTone: Boolean = false,
    val playEndTone: Boolean = false,
    val keypadToneMode: String = "Default",
    val readCallerNameAloud: Boolean = false,
    val autoAnswerEnabled: Boolean = false,
    val autoAnswerDelaySeconds: Int = 5,
    val volumeUpToAnswer: Boolean = false,
    val powerKeyEndsCall: Boolean = false,
    val callDisplayMode: String = "Small pop-up",
    val keepCallsInPopup: Boolean = false,
    val defaultSimMode: String = "Ask always",
    val showCarrierLocationTag: Boolean = true,
    val hideContactsWithoutNumbers: Boolean = true,
    val openToKeypad: Boolean = false,
    val assistedDialingIndia: Boolean = true,
)

/**
 * DataStore preferences key definitions.
 */
object PreferencesKeys {
    val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
    val DEFAULT_SIM_SLOT = intPreferencesKey("default_sim_slot")
    val ANC_LEVEL = floatPreferencesKey("anc_level")
    val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    val HAPTIC_STRENGTH = stringPreferencesKey("haptic_strength")
    val FLIP_TO_SILENCE = booleanPreferencesKey("flip_to_silence")
    val SPAM_SHIELD_ENABLED = booleanPreferencesKey("spam_shield_enabled")
    val BLOCK_UNKNOWN_NUMBERS = booleanPreferencesKey("block_unknown_numbers")
    val BLOCKED_NUMBERS_SET = stringSetPreferencesKey("blocked_numbers_set")
    val AUTO_RECORD_CALLS = booleanPreferencesKey("auto_record_calls")
    val SHOW_RECORDING_NOTIFICATION = booleanPreferencesKey("show_recording_notification")
    val CALL_BACKGROUND_THEME = stringPreferencesKey("call_background_theme")
    val CUSTOM_CALL_BACKGROUND_URI = stringPreferencesKey("custom_call_background_uri")
    val INCOMING_CALL_LAYOUT_STYLE = stringPreferencesKey("incoming_call_layout_style")
    val SHOW_CALLER_HISTORY_IN_CALL = booleanPreferencesKey("show_caller_history_in_call")
    val VIBRATE_ON_ANSWER = booleanPreferencesKey("vibrate_on_answer")
    val VIBRATE_ON_END = booleanPreferencesKey("vibrate_on_end")
    val PLAY_CONNECT_TONE = booleanPreferencesKey("play_connect_tone")
    val PLAY_END_TONE = booleanPreferencesKey("play_end_tone")
    val KEYPAD_TONE_MODE = stringPreferencesKey("keypad_tone_mode")
    val READ_CALLER_NAME_ALOUD = booleanPreferencesKey("read_caller_name_aloud")
    val AUTO_ANSWER_ENABLED = booleanPreferencesKey("auto_answer_enabled")
    val AUTO_ANSWER_DELAY_SECONDS = intPreferencesKey("auto_answer_delay_seconds")
    val VOLUME_UP_TO_ANSWER = booleanPreferencesKey("volume_up_to_answer")
    val POWER_KEY_ENDS_CALL = booleanPreferencesKey("power_key_ends_call")
    val CALL_DISPLAY_MODE = stringPreferencesKey("call_display_mode")
    val KEEP_CALLS_IN_POPUP = booleanPreferencesKey("keep_calls_in_popup")
    val DEFAULT_SIM_MODE = stringPreferencesKey("default_sim_mode")
    val SHOW_CARRIER_LOCATION_TAG = booleanPreferencesKey("show_carrier_location_tag")
    val HIDE_CONTACTS_WITHOUT_NUMBERS = booleanPreferencesKey("hide_contacts_without_numbers")
    val OPEN_TO_KEYPAD = booleanPreferencesKey("open_to_keypad")
    val ASSISTED_DIALING_INDIA = booleanPreferencesKey("assisted_dialing_india")

    val MY_PROFILE_PREFIX = stringPreferencesKey("my_profile_prefix")
    val MY_PROFILE_FIRST_NAME = stringPreferencesKey("my_profile_first_name")
    val MY_PROFILE_MIDDLE_NAME = stringPreferencesKey("my_profile_middle_name")
    val MY_PROFILE_LAST_NAME = stringPreferencesKey("my_profile_last_name")
    val MY_PROFILE_SUFFIX = stringPreferencesKey("my_profile_suffix")
    val MY_PROFILE_DISPLAY_NAME = stringPreferencesKey("my_profile_display_name")
    val MY_PROFILE_PHONE = stringPreferencesKey("my_profile_phone")
    val MY_PROFILE_EMAIL = stringPreferencesKey("my_profile_email")
    val MY_PROFILE_ADDRESS = stringPreferencesKey("my_profile_address")
    val MY_PROFILE_NOTE = stringPreferencesKey("my_profile_note")
    val MY_PROFILE_PHOTO_URI = stringPreferencesKey("my_profile_photo_uri")
    val MY_PROFILE_CALLING_CARD_URI = stringPreferencesKey("my_profile_calling_card_uri")
}

/**
 * Manager handling thread-safe Jetpack DataStore preferences reading and updating for Aether Call.
 */
class AppSettingsManager(private val context: Context) {

    val settingsFlow: Flow<UserAppSettings> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            UserAppSettings(
                appThemeMode = prefs[PreferencesKeys.APP_THEME_MODE] ?: AppThemeMode.SYSTEM.id,
                defaultSimSlot = prefs[PreferencesKeys.DEFAULT_SIM_SLOT] ?: -1,
                ancLevel = prefs[PreferencesKeys.ANC_LEVEL] ?: 0.5f,
                hapticsEnabled = prefs[PreferencesKeys.HAPTICS_ENABLED] ?: true,
                hapticStrength = prefs[PreferencesKeys.HAPTIC_STRENGTH] ?: "MEDIUM",
                flipToSilence = prefs[PreferencesKeys.FLIP_TO_SILENCE] ?: true,
                spamShieldEnabled = prefs[PreferencesKeys.SPAM_SHIELD_ENABLED] ?: true,
                blockUnknownNumbers = prefs[PreferencesKeys.BLOCK_UNKNOWN_NUMBERS] ?: false,
                blockedNumbers = prefs[PreferencesKeys.BLOCKED_NUMBERS_SET] ?: emptySet(),
                autoRecordCalls = prefs[PreferencesKeys.AUTO_RECORD_CALLS] ?: false,
                showRecordingNotification = prefs[PreferencesKeys.SHOW_RECORDING_NOTIFICATION] ?: true,
                callBackgroundTheme = prefs[PreferencesKeys.CALL_BACKGROUND_THEME] ?: "GRAD_AUTO_AVATAR",
                customCallBackgroundUri = prefs[PreferencesKeys.CUSTOM_CALL_BACKGROUND_URI],
                incomingCallLayoutStyle = prefs[PreferencesKeys.INCOMING_CALL_LAYOUT_STYLE] ?: IncomingCallLayoutStyle.SLIDE_TO_ANSWER.id,
                showCallerHistoryInCall = prefs[PreferencesKeys.SHOW_CALLER_HISTORY_IN_CALL] ?: true,
                vibrateOnAnswer = prefs[PreferencesKeys.VIBRATE_ON_ANSWER] ?: true,
                vibrateOnEnd = prefs[PreferencesKeys.VIBRATE_ON_END] ?: true,
                playConnectTone = prefs[PreferencesKeys.PLAY_CONNECT_TONE] ?: false,
                playEndTone = prefs[PreferencesKeys.PLAY_END_TONE] ?: false,
                keypadToneMode = prefs[PreferencesKeys.KEYPAD_TONE_MODE] ?: "Default",
                readCallerNameAloud = prefs[PreferencesKeys.READ_CALLER_NAME_ALOUD] ?: false,
                autoAnswerEnabled = prefs[PreferencesKeys.AUTO_ANSWER_ENABLED] ?: false,
                autoAnswerDelaySeconds = prefs[PreferencesKeys.AUTO_ANSWER_DELAY_SECONDS] ?: 5,
                volumeUpToAnswer = prefs[PreferencesKeys.VOLUME_UP_TO_ANSWER] ?: false,
                powerKeyEndsCall = prefs[PreferencesKeys.POWER_KEY_ENDS_CALL] ?: false,
                callDisplayMode = prefs[PreferencesKeys.CALL_DISPLAY_MODE] ?: "Small pop-up",
                keepCallsInPopup = prefs[PreferencesKeys.KEEP_CALLS_IN_POPUP] ?: false,
                defaultSimMode = prefs[PreferencesKeys.DEFAULT_SIM_MODE] ?: "Ask always",
                showCarrierLocationTag = prefs[PreferencesKeys.SHOW_CARRIER_LOCATION_TAG] ?: true,
                hideContactsWithoutNumbers = prefs[PreferencesKeys.HIDE_CONTACTS_WITHOUT_NUMBERS] ?: true,
                openToKeypad = prefs[PreferencesKeys.OPEN_TO_KEYPAD] ?: false,
                assistedDialingIndia = prefs[PreferencesKeys.ASSISTED_DIALING_INDIA] ?: true,
            )
        }

    val myProfileFlow: Flow<ContactItem> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val prefix = prefs[PreferencesKeys.MY_PROFILE_PREFIX] ?: "Mr."
            val firstName = prefs[PreferencesKeys.MY_PROFILE_FIRST_NAME] ?: "Naresh Kumar"
            val lastName = prefs[PreferencesKeys.MY_PROFILE_LAST_NAME] ?: "Meena"
            val middleName = prefs[PreferencesKeys.MY_PROFILE_MIDDLE_NAME] ?: ""
            val suffix = prefs[PreferencesKeys.MY_PROFILE_SUFFIX] ?: ""
            val phone = prefs[PreferencesKeys.MY_PROFILE_PHONE] ?: ""
            val email = prefs[PreferencesKeys.MY_PROFILE_EMAIL] ?: ""
            val address = prefs[PreferencesKeys.MY_PROFILE_ADDRESS] ?: ""
            val note = prefs[PreferencesKeys.MY_PROFILE_NOTE] ?: ""
            val photoUri = prefs[PreferencesKeys.MY_PROFILE_PHOTO_URI]
            val callingCardUri = prefs[PreferencesKeys.MY_PROFILE_CALLING_CARD_URI]

            val displayName = listOf(prefix, firstName, middleName, lastName, suffix)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Mr. Naresh Kumar Meena" }

            ContactItem(
                id = -1L,
                prefix = prefix,
                firstName = firstName,
                middleName = middleName,
                lastName = lastName,
                suffix = suffix,
                displayName = displayName,
                phones = if (phone.isNotBlank()) listOf(LabeledValue(value = phone, label = "Mobile")) else emptyList(),
                emails = if (email.isNotBlank()) listOf(LabeledValue(value = email, label = "Personal")) else emptyList(),
                addresses = if (address.isNotBlank()) listOf(LabeledValue(value = address, label = "Home")) else emptyList(),
                note = note,
                photoUri = photoUri,
                callingCardPhotoUri = callingCardUri,
                linkedAccounts = listOf("Device", "Google"),
            )
        }

    suspend fun updateMyProfile(contact: ContactItem) {
        context.dataStore.edit { prefs ->
            prefs[PreferencesKeys.MY_PROFILE_PREFIX] = contact.prefix
            prefs[PreferencesKeys.MY_PROFILE_FIRST_NAME] = contact.firstName
            prefs[PreferencesKeys.MY_PROFILE_MIDDLE_NAME] = contact.middleName
            prefs[PreferencesKeys.MY_PROFILE_LAST_NAME] = contact.lastName
            prefs[PreferencesKeys.MY_PROFILE_SUFFIX] = contact.suffix
            prefs[PreferencesKeys.MY_PROFILE_DISPLAY_NAME] = contact.displayName
            prefs[PreferencesKeys.MY_PROFILE_PHONE] = contact.primaryNumber
            prefs[PreferencesKeys.MY_PROFILE_EMAIL] = contact.emails.firstOrNull()?.value ?: ""
            prefs[PreferencesKeys.MY_PROFILE_ADDRESS] = contact.addresses.firstOrNull()?.value ?: ""
            prefs[PreferencesKeys.MY_PROFILE_NOTE] = contact.note
            if (contact.photoUri != null) {
                prefs[PreferencesKeys.MY_PROFILE_PHOTO_URI] = contact.photoUri
            } else {
                prefs.remove(PreferencesKeys.MY_PROFILE_PHOTO_URI)
            }
            if (contact.callingCardPhotoUri != null) {
                prefs[PreferencesKeys.MY_PROFILE_CALLING_CARD_URI] = contact.callingCardPhotoUri
            } else {
                prefs.remove(PreferencesKeys.MY_PROFILE_CALLING_CARD_URI)
            }
        }
    }

    suspend fun updateSettings(transform: (UserAppSettings) -> UserAppSettings) {
        context.dataStore.edit { prefs ->
            val current = UserAppSettings(
                appThemeMode = prefs[PreferencesKeys.APP_THEME_MODE] ?: AppThemeMode.SYSTEM.id,
                defaultSimSlot = prefs[PreferencesKeys.DEFAULT_SIM_SLOT] ?: -1,
                ancLevel = prefs[PreferencesKeys.ANC_LEVEL] ?: 0.5f,
                hapticsEnabled = prefs[PreferencesKeys.HAPTICS_ENABLED] ?: true,
                hapticStrength = prefs[PreferencesKeys.HAPTIC_STRENGTH] ?: "MEDIUM",
                flipToSilence = prefs[PreferencesKeys.FLIP_TO_SILENCE] ?: true,
                spamShieldEnabled = prefs[PreferencesKeys.SPAM_SHIELD_ENABLED] ?: true,
                blockUnknownNumbers = prefs[PreferencesKeys.BLOCK_UNKNOWN_NUMBERS] ?: false,
                blockedNumbers = prefs[PreferencesKeys.BLOCKED_NUMBERS_SET] ?: emptySet(),
                autoRecordCalls = prefs[PreferencesKeys.AUTO_RECORD_CALLS] ?: false,
                showRecordingNotification = prefs[PreferencesKeys.SHOW_RECORDING_NOTIFICATION] ?: true,
                callBackgroundTheme = prefs[PreferencesKeys.CALL_BACKGROUND_THEME] ?: "AMOLED Black",
                customCallBackgroundUri = prefs[PreferencesKeys.CUSTOM_CALL_BACKGROUND_URI],
                showCallerHistoryInCall = prefs[PreferencesKeys.SHOW_CALLER_HISTORY_IN_CALL] ?: true,
                vibrateOnAnswer = prefs[PreferencesKeys.VIBRATE_ON_ANSWER] ?: true,
                vibrateOnEnd = prefs[PreferencesKeys.VIBRATE_ON_END] ?: true,
                playConnectTone = prefs[PreferencesKeys.PLAY_CONNECT_TONE] ?: false,
                playEndTone = prefs[PreferencesKeys.PLAY_END_TONE] ?: false,
                keypadToneMode = prefs[PreferencesKeys.KEYPAD_TONE_MODE] ?: "Default",
                readCallerNameAloud = prefs[PreferencesKeys.READ_CALLER_NAME_ALOUD] ?: false,
                autoAnswerEnabled = prefs[PreferencesKeys.AUTO_ANSWER_ENABLED] ?: false,
                autoAnswerDelaySeconds = prefs[PreferencesKeys.AUTO_ANSWER_DELAY_SECONDS] ?: 5,
                volumeUpToAnswer = prefs[PreferencesKeys.VOLUME_UP_TO_ANSWER] ?: false,
                powerKeyEndsCall = prefs[PreferencesKeys.POWER_KEY_ENDS_CALL] ?: false,
                callDisplayMode = prefs[PreferencesKeys.CALL_DISPLAY_MODE] ?: "Small pop-up",
                keepCallsInPopup = prefs[PreferencesKeys.KEEP_CALLS_IN_POPUP] ?: false,
                defaultSimMode = prefs[PreferencesKeys.DEFAULT_SIM_MODE] ?: "Ask always",
                showCarrierLocationTag = prefs[PreferencesKeys.SHOW_CARRIER_LOCATION_TAG] ?: true,
                hideContactsWithoutNumbers = prefs[PreferencesKeys.HIDE_CONTACTS_WITHOUT_NUMBERS] ?: true,
                openToKeypad = prefs[PreferencesKeys.OPEN_TO_KEYPAD] ?: false,
                assistedDialingIndia = prefs[PreferencesKeys.ASSISTED_DIALING_INDIA] ?: true,
            )
            val updated = transform(current)
            prefs[PreferencesKeys.APP_THEME_MODE] = updated.appThemeMode
            prefs[PreferencesKeys.DEFAULT_SIM_SLOT] = updated.defaultSimSlot
            prefs[PreferencesKeys.ANC_LEVEL] = updated.ancLevel
            prefs[PreferencesKeys.HAPTICS_ENABLED] = updated.hapticsEnabled
            prefs[PreferencesKeys.HAPTIC_STRENGTH] = updated.hapticStrength
            prefs[PreferencesKeys.FLIP_TO_SILENCE] = updated.flipToSilence
            prefs[PreferencesKeys.SPAM_SHIELD_ENABLED] = updated.spamShieldEnabled
            prefs[PreferencesKeys.BLOCK_UNKNOWN_NUMBERS] = updated.blockUnknownNumbers
            prefs[PreferencesKeys.BLOCKED_NUMBERS_SET] = updated.blockedNumbers
            prefs[PreferencesKeys.AUTO_RECORD_CALLS] = updated.autoRecordCalls
            prefs[PreferencesKeys.SHOW_RECORDING_NOTIFICATION] = updated.showRecordingNotification
            prefs[PreferencesKeys.CALL_BACKGROUND_THEME] = updated.callBackgroundTheme
            prefs[PreferencesKeys.INCOMING_CALL_LAYOUT_STYLE] = updated.incomingCallLayoutStyle
            if (updated.customCallBackgroundUri != null) {
                prefs[PreferencesKeys.CUSTOM_CALL_BACKGROUND_URI] = updated.customCallBackgroundUri
            } else {
                prefs.remove(PreferencesKeys.CUSTOM_CALL_BACKGROUND_URI)
            }
            prefs[PreferencesKeys.SHOW_CALLER_HISTORY_IN_CALL] = updated.showCallerHistoryInCall
            prefs[PreferencesKeys.VIBRATE_ON_ANSWER] = updated.vibrateOnAnswer
            prefs[PreferencesKeys.VIBRATE_ON_END] = updated.vibrateOnEnd
            prefs[PreferencesKeys.PLAY_CONNECT_TONE] = updated.playConnectTone
            prefs[PreferencesKeys.PLAY_END_TONE] = updated.playEndTone
            prefs[PreferencesKeys.KEYPAD_TONE_MODE] = updated.keypadToneMode
            prefs[PreferencesKeys.READ_CALLER_NAME_ALOUD] = updated.readCallerNameAloud
            prefs[PreferencesKeys.AUTO_ANSWER_ENABLED] = updated.autoAnswerEnabled
            prefs[PreferencesKeys.AUTO_ANSWER_DELAY_SECONDS] = updated.autoAnswerDelaySeconds
            prefs[PreferencesKeys.VOLUME_UP_TO_ANSWER] = updated.volumeUpToAnswer
            prefs[PreferencesKeys.POWER_KEY_ENDS_CALL] = updated.powerKeyEndsCall
            prefs[PreferencesKeys.CALL_DISPLAY_MODE] = updated.callDisplayMode
            prefs[PreferencesKeys.KEEP_CALLS_IN_POPUP] = updated.keepCallsInPopup
            prefs[PreferencesKeys.DEFAULT_SIM_MODE] = updated.defaultSimMode
            prefs[PreferencesKeys.SHOW_CARRIER_LOCATION_TAG] = updated.showCarrierLocationTag
            prefs[PreferencesKeys.HIDE_CONTACTS_WITHOUT_NUMBERS] = updated.hideContactsWithoutNumbers
            prefs[PreferencesKeys.OPEN_TO_KEYPAD] = updated.openToKeypad
            prefs[PreferencesKeys.ASSISTED_DIALING_INDIA] = updated.assistedDialingIndia
        }
    }

    suspend fun updateAppThemeMode(mode: String) = updateSettings { it.copy(appThemeMode = mode) }
    suspend fun setDefaultSimSlot(slot: Int) = updateSettings { it.copy(defaultSimSlot = slot) }
    suspend fun setAncLevel(level: Float) = updateSettings { it.copy(ancLevel = level) }
    suspend fun setHapticsEnabled(enabled: Boolean) = updateSettings { it.copy(hapticsEnabled = enabled) }
    suspend fun setHapticStrength(strength: String) = updateSettings { it.copy(hapticStrength = strength) }
    suspend fun setFlipToSilence(enabled: Boolean) = updateSettings { it.copy(flipToSilence = enabled) }
    suspend fun setSpamShieldEnabled(enabled: Boolean) = updateSettings { it.copy(spamShieldEnabled = enabled) }
    suspend fun setCallDisplayMode(mode: String) = updateSettings { it.copy(callDisplayMode = mode) }
    suspend fun setAutoRecordCalls(enabled: Boolean) = updateSettings { it.copy(autoRecordCalls = enabled) }
    suspend fun updateCallBackgroundTheme(theme: String) = updateSettings { it.copy(callBackgroundTheme = theme) }
    suspend fun updateCustomCallBackgroundUri(uri: String?) = updateSettings { it.copy(customCallBackgroundUri = uri) }
    suspend fun updateIncomingCallLayoutStyle(style: String) = updateSettings { it.copy(incomingCallLayoutStyle = style) }
}
