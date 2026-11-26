package org.suppressingfire.wemi.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SwitchDao {
    @Query("SELECT * FROM wemo_devices ORDER BY sortOrder ASC, id ASC")
    fun getAllDevices(): Flow<List<DeviceEntity>>

    @Query("SELECT COUNT(*) FROM wemo_devices")
    suspend fun getDeviceCount(): Int

    @Query("SELECT * FROM wemo_devices WHERE id = :id LIMIT 1")
    suspend fun getDeviceById(id: Int): DeviceEntity?

    @Query("SELECT * FROM wemo_devices WHERE LOWER(serialNumber) = LOWER(:serialNumber) LIMIT 1")
    suspend fun getDeviceBySerial(serialNumber: String): DeviceEntity?

    @Query("SELECT * FROM wemo_devices WHERE ipAddress = :ipAddress LIMIT 1")
    suspend fun getDeviceByIp(ipAddress: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: DeviceEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<DeviceEntity>)

    @Update
    suspend fun updateDevice(device: DeviceEntity)

    @Query("DELETE FROM wemo_devices WHERE id = :id")
    suspend fun deleteDeviceById(id: Int)

    @Query("DELETE FROM wemo_devices")
    suspend fun deleteAllDevices()

    @Query("SELECT * FROM wemo_home_networks ORDER BY sortOrder ASC, homeName ASC")
    fun getAllHomeNetworks(): Flow<List<HomeNetworkEntity>>

    @Query("SELECT COUNT(*) FROM wemo_home_networks")
    suspend fun getHomeNetworkCount(): Int

    @Query("SELECT * FROM wemo_home_networks WHERE networkKey = :networkKey LIMIT 1")
    suspend fun getHomeNetworkByKey(networkKey: String): HomeNetworkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeNetwork(network: HomeNetworkEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHomeNetworks(networks: List<HomeNetworkEntity>)

    @Update
    suspend fun updateHomeNetwork(network: HomeNetworkEntity)

    @Query("DELETE FROM wemo_home_networks WHERE networkKey = :networkKey")
    suspend fun deleteHomeNetworkByKey(networkKey: String)

    @Query("DELETE FROM wemo_home_networks")
    suspend fun deleteAllHomeNetworks()
}
