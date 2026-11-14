package com.aether.call.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room Entity representing quick SMS decline message response templates.
 */
@Entity(tableName = "quick_reply_templates")
data class QuickReplyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val messageText: String,
    val sortOrder: Int = 0,
    val isDefaultTemplate: Boolean = false,
)

/**
 * Data Access Object for managing quick SMS decline templates.
 */
@Dao
interface QuickReplyDao {
    @Query("SELECT * FROM quick_reply_templates ORDER BY sortOrder ASC, id ASC")
    fun getAllTemplates(): Flow<List<QuickReplyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: QuickReplyEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(templates: List<QuickReplyEntity>)

    @Update
    suspend fun updateTemplate(template: QuickReplyEntity)

    @Delete
    suspend fun deleteTemplate(template: QuickReplyEntity)

    @Query("SELECT COUNT(*) FROM quick_reply_templates")
    suspend fun getCount(): Int
}
