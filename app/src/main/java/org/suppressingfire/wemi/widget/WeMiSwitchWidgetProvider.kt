package org.suppressingfire.wemi.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.data.SwitchRepository
import org.suppressingfire.wemi.network.LanRuntimeUtils
import org.suppressingfire.wemi.network.SharedSoapLogStore

/**
 * Android Home Screen AppWidgetProvider for one-tap Light & Room power control without opening
 * the app. Uses `goAsync()` with a bounded `8.5s` timeout so local-LAN UPnP SOAP toggles complete
 * cleanly inside the broadcast lifecycle without risking an ANR.
 */
class WeMiSwitchWidgetProvider : AppWidgetProvider() {

    private fun runAsyncBroadcast(
        context: Context,
        actionLabel: String,
        block: suspend () -> Unit
    ) {
        val customRunner = actionRunnerForTests
        if (customRunner != null) {
            customRunner(block)
            return
        }
        val appCtx = context.applicationContext
        val pendingResult = goAsync()
        widgetScope.launch {
            try {
                val completed = withTimeoutOrNull(broadcastTimeoutMs) {
                    block()
                    true
                }
                if (completed == null) {
                    SharedSoapLogStore.append(
                        context = appCtx,
                        entry = SoapLogEntry(
                            timestamp = SimpleDateFormat("h:mm:ss a", Locale.US).format(Date()),
                            deviceName = "Home Screen Widget",
                            endpoint = "widget://$actionLabel",
                            soapAction = "Widget#$actionLabel",
                            statusText = "Timeout • Exceeded ${broadcastTimeoutMs}ms broadcast budget",
                            xmlSnippet = "",
                            isLiveLanSuccess = false
                        )
                    )
                }
            } finally {
                pendingResult?.finish()
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        runAsyncBroadcast(context, "Refresh(${appWidgetIds.joinToString(",")})") {
            WidgetActionExecutor.refreshWidgetIds(
                context = context,
                appWidgetIds = appWidgetIds,
                repositoryOverride = repositoryProviderForTests?.invoke(context)
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE_WIDGET -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                if (appWidgetId <= 0) return
                runAsyncBroadcast(context, "Toggle($appWidgetId)") {
                    WidgetActionExecutor.toggleWidgetTarget(
                        context = context,
                        appWidgetId = appWidgetId,
                        repositoryOverride = repositoryProviderForTests?.invoke(context)
                    )
                }
            }

            ACTION_WIDGET_PINNED -> {
                val appWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID
                )
                val targetType = WidgetTargetType.fromRaw(intent.getStringExtra(EXTRA_TARGET_TYPE))
                if (appWidgetId <= 0 || targetType == null) return

                val rawSerial = intent.getStringExtra(EXTRA_SERIAL_NUMBER).orEmpty()
                val rawUdn = intent.getStringExtra(EXTRA_UDN).orEmpty()
                val config = WidgetTargetConfig(
                    appWidgetId = appWidgetId,
                    targetType = targetType,
                    deviceId = intent.getIntExtra(EXTRA_DEVICE_ID, 0),
                    serialNumber = rawSerial.takeUnless { LanRuntimeUtils.isPlaceholderSerial(it) }.orEmpty(),
                    roomName = intent.getStringExtra(EXTRA_ROOM_NAME).orEmpty(),
                    networkKey = intent.getStringExtra(EXTRA_NETWORK_KEY).orEmpty(),
                    displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME).orEmpty(),
                    udn = LanRuntimeUtils.canonicalHardwareUdn(rawUdn)
                )
                WidgetConfigStore.save(context, config)

                runAsyncBroadcast(context, "Pinned($appWidgetId)") {
                    WidgetActionExecutor.refreshWidgetIds(
                        context = context,
                        appWidgetIds = intArrayOf(appWidgetId),
                        repositoryOverride = repositoryProviderForTests?.invoke(context)
                    )
                }
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { widgetId ->
            WidgetConfigStore.delete(context, widgetId)
            WidgetActionExecutor.clearWidgetMutex(widgetId)
        }
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray, newWidgetIds: IntArray) {
        WidgetConfigStore.remapIds(context, oldWidgetIds, newWidgetIds)
        val retainedNewIds = newWidgetIds.toSet()
        oldWidgetIds.forEach { oldId ->
            if (oldId !in retainedNewIds) {
                WidgetActionExecutor.clearWidgetMutex(oldId)
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_WIDGET = "org.suppressingfire.wemi.widget.ACTION_TOGGLE_WIDGET"
        const val ACTION_WIDGET_PINNED = "org.suppressingfire.wemi.widget.ACTION_WIDGET_PINNED"

        const val EXTRA_TARGET_TYPE = "extra_widget_target_type"
        const val EXTRA_DEVICE_ID = "extra_widget_device_id"
        const val EXTRA_SERIAL_NUMBER = "extra_widget_serial_number"
        const val EXTRA_UDN = "extra_widget_udn"
        const val EXTRA_ROOM_NAME = "extra_widget_room_name"
        const val EXTRA_NETWORK_KEY = "extra_widget_network_key"
        const val EXTRA_DISPLAY_NAME = "extra_widget_display_name"

        private const val BROADCAST_TIMEOUT_MS = 8_500L
        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        @Volatile
        internal var broadcastTimeoutMs: Long = BROADCAST_TIMEOUT_MS

        @Volatile
        internal var actionRunnerForTests: ((suspend () -> Unit) -> Unit)? = null

        @Volatile
        internal var repositoryProviderForTests: ((Context) -> SwitchRepository)? = null

        internal fun cancelAndJoinPendingWorkForTests() {
            val pendingJobs = widgetScope.coroutineContext[Job]?.children?.toList().orEmpty()
            if (pendingJobs.isEmpty()) return
            runBlocking {
                pendingJobs.forEach { it.cancelAndJoin() }
            }
        }
    }
}
