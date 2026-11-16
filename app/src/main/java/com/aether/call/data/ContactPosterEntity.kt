package com.aether.call.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Room Entity storing custom iOS 17-style contact posters and theme overrides.
 */
@Entity(tableName = "contact_posters")
data class ContactPosterEntity(
    @PrimaryKey val phoneNumber: String,
    val customPhotoUri: String? = null,
    val posterTheme: String = "DEFAULT",
    val customNickname: String? = null,
)

/**
 * Data Access Object for contact posters.
 */
@Dao
interface ContactPosterDao {

    @Query("SELECT * FROM contact_posters WHERE phoneNumber = :number LIMIT 1")
    suspend fun getPosterForNumber(number: String): ContactPosterEntity?

    @Query("SELECT * FROM contact_posters")
    suspend fun getAllPosters(): List<ContactPosterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePoster(poster: ContactPosterEntity)
}

/**
 * Room Database for Aether Call custom contact posters and local profile configurations.
 */
@Database(
    entities = [
        ContactPosterEntity::class,
        ContactFtsEntity::class,
        ContactNoteEntity::class,
        SimPreferenceEntity::class,
        BlockedNumberEntity::class,
        QuickReplyEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AetherDatabase : RoomDatabase() {

    abstract fun contactPosterDao(): ContactPosterDao
    abstract fun contactSearchDao(): ContactSearchDao
    abstract fun contactNoteDao(): ContactNoteDao
    abstract fun simPreferenceDao(): SimPreferenceDao
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun quickReplyDao(): QuickReplyDao

    companion object {
        @Volatile
        private var INSTANCE: AetherDatabase? = null

        /**
         * Returns singleton [AetherDatabase] instance.
         */
        fun getDatabase(context: Context): AetherDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AetherDatabase::class.java,
                    "aether_database",
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                            super.onCreate(db)
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                INSTANCE?.quickReplyDao()?.insertAll(
                                    listOf(
                                        QuickReplyEntity(messageText = "Can't talk now. What's up?", sortOrder = 0, isDefaultTemplate = true),
                                        QuickReplyEntity(messageText = "I'll call you right back.", sortOrder = 1, isDefaultTemplate = true),
                                        QuickReplyEntity(messageText = "I'm in a meeting.", sortOrder = 2, isDefaultTemplate = true),
                                        QuickReplyEntity(messageText = "On my way.", sortOrder = 3, isDefaultTemplate = true),
                                    ),
                                )
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}