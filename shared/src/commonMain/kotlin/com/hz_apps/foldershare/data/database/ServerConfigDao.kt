package com.hz_apps.foldershare.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {
    @Query("SELECT * FROM server_config WHERE id = 1 LIMIT 1")
    fun getServerConfigFlow(): Flow<ServerConfigEntity?>

    @Query("SELECT * FROM server_config WHERE id = 1 LIMIT 1")
    suspend fun getServerConfig(): ServerConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateServerConfig(config: ServerConfigEntity)

    @Update
    suspend fun updateServerConfig(config: ServerConfigEntity)
}
