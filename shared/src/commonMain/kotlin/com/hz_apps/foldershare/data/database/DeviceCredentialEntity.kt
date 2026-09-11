package com.hz_apps.foldershare.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_credential")
data class DeviceCredentialEntity(
    @PrimaryKey val deviceId: String,
    val username: String,
    val password: String,
    val lastUpdated: Long = System.currentTimeMillis()
)
