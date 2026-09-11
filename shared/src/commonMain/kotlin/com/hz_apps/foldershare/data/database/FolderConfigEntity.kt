package com.hz_apps.foldershare.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folder_config")
data class FolderConfigEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val path: String,
    val iconEmoji: String = "📁",
    val isReadAllowed: Boolean = true,
    val isWriteAllowed: Boolean = false,
    val isShared: Boolean = true
)
