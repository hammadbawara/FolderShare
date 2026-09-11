package com.hz_apps.foldershare.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.hz_apps.foldershare.core.util.PlatformDirs

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val dbFile = PlatformDirs.dataDir / "foldershare.db"
    return Room.databaseBuilder<AppDatabase>(
        name = dbFile.toString()
    )
}
