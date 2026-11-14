package com.aether.call.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Room entity storing user SIM slot preferences (SIM 1 vs SIM 2) for specific phone numbers.
 */
@Entity(tableName = "sim_preferences")
data class SimPreferenceEntity(
    @PrimaryKey val phoneNumber: String,
    val preferredSlotIndex: Int, // 0 for SIM 1, 1 for SIM 2
)

/**
 * Data Access Object for SIM slot preferences.
 */
@Dao
interface SimPreferenceDao {

    @Query("SELECT preferredSlotIndex FROM sim_preferences WHERE phoneNumber = :number LIMIT 1")
    suspend fun getPreferredSlot(number: String): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun savePreferredSlot(pref: SimPreferenceEntity)
}
