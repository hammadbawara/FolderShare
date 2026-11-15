package com.hz_apps.foldershare.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientPreferenceDao {
    @Query("SELECT * FROM client_preference WHERE id = 1 LIMIT 1")
    fun getClientPreferenceFlow(): Flow<ClientPreferenceEntity?>

    @Query("SELECT * FROM client_preference WHERE id = 1 LIMIT 1")
    suspend fun getClientPreference(): ClientPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateClientPreference(preference: ClientPreferenceEntity)
}
