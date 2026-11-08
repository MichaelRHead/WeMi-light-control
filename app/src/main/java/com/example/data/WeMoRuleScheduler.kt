package com.example.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * App-driven coroutine scheduler for Belkin WeMo local rules & timers:
 * 1. Manages per-device Auto-Off countdown timers (from WeMoDeviceEntity.autoOffMinutes or
 *    enabled AUTO_OFF rules) that automatically turn switches OFF when the timer expires.
 * 2. Evaluates enabled SCHEDULE rules (e.g. "Daily at 7:00 PM", "Daily at Sunset (5:15 PM)")
 *    and dispatches them over LAN via WeMoRepository.
 */
class WeMoRuleScheduler(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val onAutoOffExpired: suspend (deviceId: Int) -> Unit,
    private val onScheduleRuleTriggered: suspend (rule: WeMoRuleEntity) -> Unit
) {
    private val countdownJobs = ConcurrentHashMap<Int, Job>()
    private val _activeCountdownsSec = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val activeCountdownsSec: StateFlow<Map<Int, Int>> = _activeCountdownsSec.asStateFlow()

    private var scheduleLoopJob: Job? = null
    private val triggeredMinuteKeys = ConcurrentHashMap<Int, String>()

    /**
     * Starts or cancels the Auto-Off countdown for [device] whenever its power state changes.
     */
    fun onDeviceStateUpdated(
        device: WeMoDeviceEntity,
        rules: List<WeMoRuleEntity> = emptyList()
    ) {
        if (!device.isOn || !device.isReachable) {
            cancelAutoOff(device.id)
            return
        }

        val ruleMinutes = rules.firstOrNull { rule ->
            rule.isEnabled &&
                rule.ruleType.startsWith("AUTO_OFF", ignoreCase = true) &&
                (rule.targetDeviceId == device.id || rule.targetDeviceName.equals(device.name, ignoreCase = true))
        }?.let { parseMinutesFromSummary(it.triggerSummary) } ?: 0

        val totalMinutes = when {
            device.autoOffMinutes > 0 -> device.autoOffMinutes
            ruleMinutes > 0 -> ruleMinutes
            else -> 0
        }

        if (totalMinutes <= 0) {
            cancelAutoOff(device.id)
            return
        }

        startAutoOffCountdownSeconds(device.id, totalMinutes * 60)
    }

    /**
     * Starts an Auto-Off countdown in seconds for [deviceId] (also directly callable from tests).
     */
    fun startAutoOffCountdownSeconds(deviceId: Int, durationSeconds: Int) {
        countdownJobs.remove(deviceId)?.cancel()
        if (durationSeconds <= 0) {
            _activeCountdownsSec.value = _activeCountdownsSec.value - deviceId
            return
        }

        _activeCountdownsSec.value = _activeCountdownsSec.value + (deviceId to durationSeconds)
        val job = scope.launch {
            var remaining = durationSeconds
            while (isActive && remaining > 0) {
                _activeCountdownsSec.value = _activeCountdownsSec.value + (deviceId to remaining)
                delay(1000L)
                remaining--
            }
            _activeCountdownsSec.value = _activeCountdownsSec.value - deviceId
            countdownJobs.remove(deviceId)
            if (isActive) {
                onAutoOffExpired(deviceId)
            }
        }
        countdownJobs[deviceId] = job
    }

    fun cancelAutoOff(deviceId: Int) {
        countdownJobs.remove(deviceId)?.cancel()
        if (deviceId in _activeCountdownsSec.value) {
            _activeCountdownsSec.value = _activeCountdownsSec.value - deviceId
        }
    }

    /**
     * Starts the periodic 30-second schedule evaluator loop for [rulesProvider].
     */
    fun startScheduleLoop(rulesProvider: () -> List<WeMoRuleEntity>) {
        if (scheduleLoopJob?.isActive == true) return
        scheduleLoopJob = scope.launch {
            while (isActive) {
                val cal = Calendar.getInstance()
                val hour24 = cal.get(Calendar.HOUR_OF_DAY)
                val minute = cal.get(Calendar.MINUTE)
                val dayKey = "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}-$hour24:$minute"

                val rules = rulesProvider()
                for (rule in rules) {
                    if (!rule.isEnabled || rule.ruleType == "MOTION_TRIGGER") continue
                    val targetTime = parseScheduleTime24h(rule.triggerSummary) ?: continue
                    if (targetTime.first == hour24 && targetTime.second == minute) {
                        val prevKey = triggeredMinuteKeys[rule.id]
                        if (prevKey != dayKey) {
                            triggeredMinuteKeys[rule.id] = dayKey
                            onScheduleRuleTriggered(rule)
                        }
                    }
                }
                delay(20_000L)
            }
        }
    }

    fun stopAll() {
        scheduleLoopJob?.cancel()
        scheduleLoopJob = null
        countdownJobs.values.forEach { it.cancel() }
        countdownJobs.clear()
        _activeCountdownsSec.value = emptyMap()
    }

    companion object {
        /**
         * Parses a duration in minutes from strings like "After 30 minutes ON" or "15 min".
         */
        fun parseMinutesFromSummary(summary: String): Int {
            val match = Regex("(\\d+)\\s*(?:min|minute|minutes|m\\b)", RegexOption.IGNORE_CASE).find(summary)
            return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(1, 1440) ?: 0
        }

        /**
         * Parses a 24-hour (hour, minute) pair from trigger summaries such as:
         * - "Daily at 7:00 PM" -> (19, 0)
         * - "Daily at Sunset (5:15 PM)" -> (17, 15)
         * - "Every night at 10:00 PM" -> (22, 0)
         * - "Daily at 11:59 PM" -> (23, 59)
         * - "Daily at Sunrise (6:30 AM)" -> (6, 30)
         */
        fun parseScheduleTime24h(summary: String): Pair<Int, Int>? {
            val regex = Regex("(\\d{1,2}):(\\d{2})\\s*(AM|PM)?", RegexOption.IGNORE_CASE)
            val match = regex.find(summary)
            if (match != null) {
                val rawHour = match.groupValues[1].toIntOrNull() ?: return null
                val min = match.groupValues[2].toIntOrNull()?.coerceIn(0, 59) ?: return null
                val amPm = match.groupValues.getOrNull(3)?.uppercase(Locale.US).orEmpty()
                val hour24 = when (amPm) {
                    "PM" -> if (rawHour < 12) rawHour + 12 else rawHour
                    "AM" -> if (rawHour == 12) 0 else rawHour
                    else -> rawHour.coerceIn(0, 23)
                }
                return Pair(hour24, min)
            }
            val lower = summary.lowercase(Locale.US)
            return when {
                "sunset" in lower -> Pair(17, 30)
                "sunrise" in lower -> Pair(6, 30)
                "midnight" in lower -> Pair(0, 0)
                else -> null
            }
        }
    }
}
