package com.aether.call.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * FTS4-backed entity for zero-latency full-text search lookups across contact fields.
 */
@Fts4
@Entity(tableName = "contacts_fts")
data class ContactFtsEntity(
    @PrimaryKey
    @ColumnInfo(name = "rowid")
    val rowid: Int? = null,
    val contactId: String,
    val displayName: String,
    val phoneNumber: String,
    val organization: String = "",
    val notes: String = "",
)

/**
 * Data Access Object for FTS contact search queries.
 */
@Dao
interface ContactSearchDao {

    @Query("SELECT rowid, * FROM contacts_fts WHERE contacts_fts MATCH :query || '*' LIMIT 50")
    suspend fun searchContacts(query: String): List<ContactFtsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<ContactFtsEntity>)

    @Query("DELETE FROM contacts_fts")
    suspend fun clearIndex()
}

/**
 * Room entity storing per-contact notes, preferred SIM selection, and custom ANC override levels.
 */
@Entity(tableName = "contact_notes")
data class ContactNoteEntity(
    @PrimaryKey val phoneNumber: String,
    val note: String = "",
    val ancLevel: Float = 0.8f,
    val preferredSim: Int = 1,
)

/**
 * Data Access Object for per-contact preferences and notes.
 */
@Dao
interface ContactNoteDao {

    @Query("SELECT * FROM contact_notes WHERE phoneNumber = :number LIMIT 1")
    suspend fun getNoteForNumber(number: String): ContactNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveNote(note: ContactNoteEntity)
}
