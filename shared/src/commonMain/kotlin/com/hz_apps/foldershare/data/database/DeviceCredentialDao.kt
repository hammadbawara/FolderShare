package com.hz_apps.foldershare.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceCredentialDao {
    @Query("SELECT * FROM device_credential WHERE deviceId = :deviceId LIMIT 1")
    suspend fun getCredentialForDevice(deviceId: String): DeviceCredentialEntity?

    @Query("SELECT * FROM device_credential WHERE deviceId = :deviceId LIMIT 1")
    fun getCredentialFlowForDevice(deviceId: String): Flow<DeviceCredentialEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCredential(credential: DeviceCredentialEntity)

    @Query("DELETE FROM device_credential WHERE deviceId = :deviceId")
    suspend fun deleteCredentialForDevice(deviceId: String)
}
