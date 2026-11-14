package com.aether.call.telecom

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.aether.call.data.ContactsRepository
import com.aether.call.util.PhoneNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified caller identity model supporting all notification, overlay, and in-call screens.
 */
data class ResolvedCaller(
    val displayName: String,
    val rawNumber: String,
    val label: String = "Mobile",
    val photoUri: String? = null,
    val isSavedContact: Boolean = false,
) {
    val number: String get() = rawNumber
    val phoneNumber: String get() = rawNumber
    val isSaved: Boolean get() = isSavedContact
}

typealias ResolvedCallerInfo = ResolvedCaller

object CallerIdResolver {

    /**
     * Synchronous / background-safe caller resolution used by AetherInCallService,
     * CallNotificationManager, ActiveCallNotificationBuilder, and IncomingCallOverlayService.
     */
    fun resolveCaller(context: Context, rawNumber: String?): ResolvedCaller {
        val clean = rawNumber?.trim().orEmpty()
        if (clean.isBlank()) {
            return ResolvedCaller(
                displayName = "Unknown Caller",
                rawNumber = "",
                label = "",
                photoUri = null,
                isSavedContact = false,
            )
        }

        // 1. Try fast native PhoneLookup (handles standard E.164 and local formats)
        try {
            val lookupUri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(clean),
            )
            context.contentResolver.query(
                lookupUri,
                arrayOf(
                    ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI,
                    ContactsContract.PhoneLookup.TYPE,
                    ContactsContract.PhoneLookup.LABEL,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    val photo = cursor.getString(1)
                    val type = cursor.getInt(2)
                    val customLabel = cursor.getString(3)
                    val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        context.resources,
                        type,
                        customLabel,
                    ).toString()

                    if (!name.isNullOrBlank()) {
                        return ResolvedCaller(
                            displayName = name,
                            rawNumber = clean,
                            label = typeLabel.ifBlank { "Mobile" },
                            photoUri = photo,
                            isSavedContact = true,
                        )
                    }
                }
            }
        } catch (_: Exception) {
        }

        // 2. Fallback: Normalized trailing 10-digit PhoneLookup (strips +91, 0, spaces, hyphens)
        val norm10 = PhoneNormalizer.normalizeDigits(clean)
        if (norm10.length >= 7 && norm10 != clean) {
            try {
                val lookupUri2 = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(norm10),
                )
                context.contentResolver.query(
                    lookupUri2,
                    arrayOf(
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_URI,
                        ContactsContract.PhoneLookup.TYPE,
                        ContactsContract.PhoneLookup.LABEL,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        val photo = cursor.getString(1)
                        val type = cursor.getInt(2)
                        val customLabel = cursor.getString(3)
                        val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                            context.resources,
                            type,
                            customLabel,
                        ).toString()

                        if (!name.isNullOrBlank()) {
                            return ResolvedCaller(
                                displayName = name,
                                rawNumber = clean,
                                label = typeLabel.ifBlank { "Mobile" },
                                photoUri = photo,
                                isSavedContact = true,
                            )
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        // 3. Direct CommonDataKinds.Phone scan comparing normalized 10-digit numbers
        if (norm10.length >= 7) {
            try {
                context.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val dbNum = cursor.getString(0) ?: continue
                        if (PhoneNormalizer.normalizeDigits(dbNum) == norm10) {
                            val name = cursor.getString(1)
                            val photo = cursor.getString(2)
                            val type = cursor.getInt(3)
                            val customLabel = cursor.getString(4)
                            val typeLabel = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                                context.resources,
                                type,
                                customLabel,
                            ).toString()

                            if (!name.isNullOrBlank()) {
                                return ResolvedCaller(
                                    displayName = name,
                                    rawNumber = clean,
                                    label = typeLabel.ifBlank { "Mobile" },
                                    photoUri = photo,
                                    isSavedContact = true,
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }

        return ResolvedCaller(
            displayName = clean,
            rawNumber = clean,
            label = "Unsaved",
            photoUri = null,
            isSavedContact = false,
        )
    }

    suspend fun resolve(context: Context, rawNumber: String?): ResolvedCaller = withContext(Dispatchers.IO) {
        resolveCaller(context, rawNumber)
    }
}