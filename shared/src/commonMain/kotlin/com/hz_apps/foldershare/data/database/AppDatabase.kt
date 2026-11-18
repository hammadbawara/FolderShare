package com.hz_apps.foldershare.data.database

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import kotlinx.coroutines.Dispatchers

@Database(
    entities = [
        FolderConfigEntity::class,
        ServerConfigEntity::class,
        DeviceCredentialEntity::class,
        ClientPreferenceEntity::class
    ],
    version = 7,
    exportSchema = false
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun folderConfigDao(): FolderConfigDao
    abstract fun serverConfigDao(): ServerConfigDao
    abstract fun deviceCredentialDao(): DeviceCredentialDao
    abstract fun clientPreferenceDao(): ClientPreferenceDao
}

// The Room compiler generates the actual implementation of this constructor.
@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase>

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "CREATE TABLE IF NOT EXISTS `client_preference` (`id` INTEGER NOT NULL, `isTransferShowMoreExpanded` INTEGER NOT NULL, PRIMARY KEY(`id`))"
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL(
            "ALTER TABLE `client_preference` ADD COLUMN `hasCompletedOnboarding` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

fun getRoomDatabase(
    builder: RoomDatabase.Builder<AppDatabase>
): AppDatabase {
    return builder
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
