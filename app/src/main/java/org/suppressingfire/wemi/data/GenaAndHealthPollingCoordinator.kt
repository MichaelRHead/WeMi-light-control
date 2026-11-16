package org.suppressingfire.wemi.data

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.suppressingfire.wemi.network.GenaEventNotification
import org.suppressingfire.wemi.network.GenaSubscriptionManager
import org.suppressingfire.wemi.network.NetworkClient

/**
 * Coordinates UPnP GENA `<100ms` push event notifications (`1200ms` echo suppression),
 * concurrent `Semaphore(3)` GENA subscription renewals, concurrent `Semaphore(3)` 30-second
 * health polling (`2000ms` pre/post-probe echo guard), and Dimmer `ConfigureNightMode` state
 * transitions on behalf of [SwitchRepository].
 */
internal class GenaAndHealthPollingCoordinator(
    private val dao: SwitchDao,
    private val networkClient: NetworkClient,
    private val genaManager: GenaSubscriptionManager,
    private val lanSemaphore: Semaphore,
    private val deviceMutexes: ConcurrentHashMap<String, Mutex>,
    private val lastSetStateTimestampMs: ConcurrentHashMap<Int, Long>,
    private val isDemoShowroomMode: () -> Boolean,
    private val attemptTwoStageReconnect: suspend (DeviceEntity) -> DeviceEntity?,
    private val appendLog: (SoapLogEntry) -> Unit,
    private val nowFormatted: () -> String
) {
    private fun mutexFor(device: DeviceEntity): Mutex =
        deviceMutexes.getOrPut(device.concurrencyKey()) { Mutex() }

    /**
     * Handles incoming UPnP GENA `<e:propertyset>` push events from physical wall switches
     * and 3-way traveler rockers, updating Room state immediately (`<100ms`).
     */
    suspend fun handleGenaNotification(event: GenaEventNotification) {
        appendLog(event.logEntry)
        val devices = dao.getAllDevices().first()
        val matched = devices.find {
            (event.deviceKey.isNotBlank() && it.serialNumber.equals(event.deviceKey, ignoreCase = true)) ||
                event.deviceKey == "dev_${it.id}" ||
                (event.sourceIp.isNotBlank() && it.ipAddress == event.sourceIp)
        } ?: return

        val recentSetMs = lastSetStateTimestampMs[matched.id] ?: 0L
        if ((System.currentTimeMillis() - recentSetMs) < 1200L) {
            return
        }

        val updated = matched.copy(
            isOn = event.binaryStateOn ?: matched.isOn,
            brightness = event.brightness?.coerceIn(1, 100) ?: matched.brightness,
            isReachable = true,
            lastCheckedTime = "GENA Push • ${nowFormatted()}"
        )
        dao.updateDevice(updated)
    }

    /**
     * Subscribes or renews UPnP GENA (`Second-300`) callbacks across all reachable switches on
     * [networkKey] concurrently (bounded by [lanSemaphore] `Semaphore(3)`).
     */
    suspend fun refreshGenaSubscriptionsForHome(networkKey: String): Int = coroutineScope {
        if (isDemoShowroomMode()) return@coroutineScope 0
        val devices = dao.getAllDevices().first().filter {
            it.networkKey == networkKey && it.isReachable && it.parsedType != DeviceType.MOTION_SENSOR
        }
        if (devices.isEmpty()) return@coroutineScope 0
        devices.map { dev ->
            async {
                lanSemaphore.withPermit {
                    genaManager.subscribeOrRenew(dev, timeoutSeconds = 300) != null
                }
            }
        }.awaitAll().count { it }
    }

    /**
     * Polls `GetBinaryState` across all remembered switches on [networkKey] concurrently
     * (bounded by [lanSemaphore] `Semaphore(3)` and serialized per device via [mutexFor]),
     * skipping any switch mutated within the `2000ms` pre/post-probe echo guard window.
     */
    suspend fun pollActiveHomeStates(networkKey: String) = coroutineScope {
        if (isDemoShowroomMode()) return@coroutineScope
        val devices = dao.getAllDevices().first().filter { it.networkKey == networkKey }
        if (devices.isEmpty()) return@coroutineScope
        val checkedAt = nowFormatted()
        devices.map { dev ->
            async {
                mutexFor(dev).withLock {
                    val recentSetMs = lastSetStateTimestampMs[dev.id] ?: 0L
                    if ((System.currentTimeMillis() - recentSetMs) < 2000L) return@withLock

                    val latest = dao.getDeviceById(dev.id) ?: return@withLock
                    val probe = lanSemaphore.withPermit {
                        networkClient.getBinaryState(latest)
                    }

                    val postProbeSetMs = lastSetStateTimestampMs[dev.id] ?: 0L
                    if ((System.currentTimeMillis() - postProbeSetMs) < 2000L) return@withLock

                    val current = dao.getDeviceById(dev.id) ?: latest
                    if (probe.reachableOnLan) {
                        dao.updateDevice(
                            current.copy(
                                port = probe.resolvedPort,
                                isReachable = true,
                                isOn = probe.binaryStateOn ?: current.isOn,
                                brightness = probe.brightness ?: current.brightness,
                                latencyMs = probe.latencyMs.coerceAtLeast(1),
                                lastCheckedTime = checkedAt
                            )
                        )
                    } else {
                        dao.updateDevice(
                            current.copy(
                                isReachable = false,
                                latencyMs = 0,
                                lastCheckedTime = "Offline • Timeout"
                            )
                        )
                    }
                }
            }
        }.awaitAll()
    }

    /**
     * Toggles Smart Dimmer hardware Night Mode (`20%` soft cap via `ConfigureNightMode`) with
     * 2-stage self-healing port/IP recovery and Real-LAN offline rollback.
     */
    suspend fun toggleDimmerNightMode(device: DeviceEntity): DeviceEntity {
        val priorNightMode = device.nightModeEnabled
        val priorBrightness = device.brightness
        val targetNightMode = !priorNightMode
        val nextBrightness = if (targetNightMode && priorBrightness > 20) 20 else priorBrightness

        return mutexFor(device).withLock {
            var activeDevice = device
            var result = lanSemaphore.withPermit {
                networkClient.configureNightMode(
                    device = activeDevice,
                    enabled = targetNightMode,
                    nightBrightness = 20
                )
            }

            if (!result.reachableOnLan && !isDemoShowroomMode()) {
                val healedDevice = attemptTwoStageReconnect(activeDevice)
                if (healedDevice != null) {
                    activeDevice = healedDevice
                    result = lanSemaphore.withPermit {
                        networkClient.configureNightMode(
                            device = activeDevice,
                            enabled = targetNightMode,
                            nightBrightness = 20
                        )
                    }
                }
            }

            appendLog(result.logEntry)
            val updated = when {
                result.reachableOnLan -> {
                    lastSetStateTimestampMs[device.id] = System.currentTimeMillis()
                    activeDevice.copy(
                        nightModeEnabled = targetNightMode,
                        brightness = nextBrightness,
                        isReachable = true,
                        port = result.resolvedPort,
                        latencyMs = result.latencyMs.coerceAtLeast(1),
                        lastCheckedTime = nowFormatted()
                    )
                }
                isDemoShowroomMode() -> {
                    lastSetStateTimestampMs[device.id] = System.currentTimeMillis()
                    activeDevice.copy(
                        nightModeEnabled = targetNightMode,
                        brightness = nextBrightness,
                        isReachable = true,
                        port = result.resolvedPort,
                        latencyMs = device.latencyMs.takeIf { it > 0 } ?: 14,
                        lastCheckedTime = nowFormatted()
                    )
                }
                else -> activeDevice.copy(
                    nightModeEnabled = priorNightMode,
                    brightness = priorBrightness,
                    isReachable = false,
                    latencyMs = 0,
                    lastCheckedTime = "Offline • Timeout"
                )
            }
            dao.updateDevice(updated)
            updated
        }
    }
}
