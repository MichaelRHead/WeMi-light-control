package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WeMoDao {
    @Query("SELECT * FROM wemo_devices ORDER BY sortOrder ASC, id ASC")
    fun getAllDevices(): Flow<List<WeMoDeviceEntity>>

    @Query("SELECT COUNT(*) FROM wemo_devices")
    suspend fun getDeviceCount(): Int

    @Query("SELECT * FROM wemo_devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: Int): WeMoDeviceEntity?

    @Query("SELECT * FROM wemo_devices WHERE LOWER(serialNumber) = LOWER(:serialNumber) LIMIT 1")
    suspend fun getDeviceBySerial(serialNumber: String): WeMoDeviceEntity?

    @Query("SELECT * FROM wemo_devices WHERE ipAddress = :ipAddress LIMIT 1")
    suspend fun getDeviceByIp(ipAddress: String): WeMoDeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: WeMoDeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<WeMoDeviceEntity>)

    @Update
    suspend fun updateDevice(device: WeMoDeviceEntity)

    @Query("DELETE FROM wemo_devices WHERE id = :id")
    suspend fun deleteDeviceById(id: Int)

    @Query("DELETE FROM wemo_devices")
    suspend fun deleteAllDevices()

    @Query("SELECT * FROM wemo_home_networks ORDER BY sortOrder ASC, homeName ASC")
    fun getAllHomeNetworks(): Flow<List<WeMoHomeNetworkEntity>>

    @Query("SELECT COUNT(*) FROM wemo_home_networks")
    suspend fun getHomeNetworkCount(): Int

    @Query("SELECT * FROM wemo_home_networks WHERE networkKey = :networkKey LIMIT 1")
    suspend fun getHomeNetworkByKey(networkKey: String): WeMoHomeNetworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeNetwork(network: WeMoHomeNetworkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeNetworks(networks: List<WeMoHomeNetworkEntity>)

    @Update
    suspend fun updateHomeNetwork(network: WeMoHomeNetworkEntity)

    @Query("DELETE FROM wemo_home_networks WHERE networkKey = :networkKey")
    suspend fun deleteHomeNetworkByKey(networkKey: String)

    @Query("DELETE FROM wemo_home_networks")
    suspend fun deleteAllHomeNetworks()

    @Query("SELECT * FROM wemo_rules ORDER BY id ASC")
    fun getAllRules(): Flow<List<WeMoRuleEntity>>

    @Query("SELECT COUNT(*) FROM wemo_rules")
    suspend fun getRuleCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: WeMoRuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<WeMoRuleEntity>)

    @Update
    suspend fun updateRule(rule: WeMoRuleEntity)

    @Query("DELETE FROM wemo_rules WHERE id = :id")
    suspend fun deleteRuleById(id: Int)

    @Query("DELETE FROM wemo_rules")
    suspend fun deleteAllRules()
}
