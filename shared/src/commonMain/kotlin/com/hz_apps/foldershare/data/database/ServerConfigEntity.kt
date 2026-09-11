package com.hz_apps.foldershare.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hz_apps.foldershare.core.server.ServerConstants
import com.hz_apps.foldershare.core.util.generateUuid

@Entity(tableName = "server_config")
data class ServerConfigEntity(
    @PrimaryKey val id: Int = 1,
    val port: Int = ServerConstants.DEFAULT_PORT,
    val isAuthRequired: Boolean = false,
    val username: String = ServerConstants.DEFAULT_USERNAME,
    val password: String = ServerConstants.DEFAULT_PASSWORD,
    val isHttpsEnabled: Boolean = false,
    val deviceUuid: String = generateUuid(),
    val deviceName: String = ""
)
