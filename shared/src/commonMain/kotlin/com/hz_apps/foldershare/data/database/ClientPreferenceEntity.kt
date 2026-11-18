package com.hz_apps.foldershare.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "client_preference")
data class ClientPreferenceEntity(
    @PrimaryKey val id: Int = 1,
    val isTransferShowMoreExpanded: Boolean = false,
    val hasCompletedOnboarding: Boolean = false
)
