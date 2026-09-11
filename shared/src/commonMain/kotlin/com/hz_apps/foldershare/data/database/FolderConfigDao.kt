package com.hz_apps.foldershare.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderConfigDao {
    @Query("SELECT * FROM folder_config ORDER BY id DESC")
    fun getAllFolders(): Flow<List<FolderConfigEntity>>

    @Query("SELECT * FROM folder_config WHERE isShared = 1 ORDER BY id DESC")
    fun getActiveFolders(): Flow<List<FolderConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderConfigEntity): Long

    @Update
    suspend fun updateFolder(folder: FolderConfigEntity)

    @Delete
    suspend fun deleteFolder(folder: FolderConfigEntity)

    @Query("DELETE FROM folder_config WHERE id = :id")
    suspend fun deleteFolderById(id: Long)
}
