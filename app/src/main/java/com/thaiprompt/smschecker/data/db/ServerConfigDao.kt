package com.thaiprompt.smschecker.data.db

import androidx.room.*
import com.thaiprompt.smschecker.data.model.ServerConfig
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerConfigDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: ServerConfig): Long

    @Update
    suspend fun update(config: ServerConfig)

    @Delete
    suspend fun delete(config: ServerConfig)

    @Query("SELECT * FROM server_configs ORDER BY isDefault DESC, name ASC")
    fun getAllConfigs(): Flow<List<ServerConfig>>

    @Query("SELECT * FROM server_configs WHERE isActive = 1 ORDER BY isDefault DESC")
    suspend fun getActiveConfigs(): List<ServerConfig>

    @Query("SELECT * FROM server_configs WHERE id = :id")
    suspend fun getById(id: Long): ServerConfig?

    @Query("SELECT * FROM server_configs WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultConfig(): ServerConfig?

    @Query("UPDATE server_configs SET isDefault = 0")
    suspend fun clearDefaultFlag()

    @Query("UPDATE server_configs SET lastSyncAt = :syncTime, lastSyncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, syncTime: Long, status: String)

    @Query("SELECT * FROM server_configs WHERE LOWER(TRIM(baseUrl, '/')) = LOWER(TRIM(:baseUrl, '/')) LIMIT 1")
    suspend fun findByBaseUrl(baseUrl: String): ServerConfig?

    @Query("SELECT MIN(syncInterval) FROM server_configs WHERE isActive = 1")
    suspend fun getMinSyncInterval(): Int?

    @Query("UPDATE server_configs SET approvalMode = :mode, updatedAt = :now WHERE id = :serverId")
    suspend fun updateApprovalMode(serverId: Long, mode: String, now: Long = System.currentTimeMillis())
}
