package com.hz_apps.foldershare.data.database

import androidx.room.Room
import androidx.room.RoomDatabase
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider

actual fun getDatabaseBuilder(): RoomDatabase.Builder<AppDatabase> {
    val context = requireNotNull(AndroidContextProvider.applicationContext) {
        "AndroidContextProvider.applicationContext must be initialized before accessing Room database"
    }
    val dbFile = context.getDatabasePath("foldershare.db")
    return Room.databaseBuilder<AppDatabase>(
        context = context,
        name = dbFile.absolutePath
    )
}
