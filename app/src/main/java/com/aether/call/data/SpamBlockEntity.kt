package com.aether.call.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Room entity storing offline blocklist entries and suspected spam labels.
 */
@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey val phoneNumber: String,
    val label: String? = null,
    val isSpam: Boolean = true,
    val blockedAtTimestamp: Long = System.currentTimeMillis(),
)

/**
 * Data Access Object for blocked numbers and spam filtering queries.
 */
@Dao
interface BlockedNumberDao {

    @Query("SELECT * FROM blocked_numbers WHERE phoneNumber = :number LIMIT 1")
    suspend fun getBlockedEntry(number: String): BlockedNumberEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE phoneNumber = :number)")
    suspend fun isBlocked(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun blockNumber(entry: BlockedNumberEntity)

    @Delete
    suspend fun unblockNumber(entry: BlockedNumberEntity)

    @Query("SELECT * FROM blocked_numbers ORDER BY blockedAtTimestamp DESC")
    fun getAllBlocked(): Flow<List<BlockedNumberEntity>>
}
