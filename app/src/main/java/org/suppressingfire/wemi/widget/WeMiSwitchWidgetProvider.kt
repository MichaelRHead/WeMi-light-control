package org.suppressingfire.wemi.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
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
import org.suppressingfire.wemi.network.SharedSoapLogStore

/**
 * Android Home Screen AppWidgetProvider for standard launcher lifecycle broadcasts
 * (`APPWIDGET_UPDATE`, `APPWIDGET_DELETED`, `APPWIDGET_RESTORED`).
 *
 * Interactive toggle (`ACTION_TOGGLE_WIDGET`) and pin-binding (`ACTION_WIDGET_PINNED`) callbacks
 * are routed exclusively to the non-exported [WeMiWidgetActionReceiver] so untrusted third-party
 * apps cannot spoof widget toggles or forge widget bindings.
 */
class WeMiSwitchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        if (appWidgetIds.isEmpty()) return
        dispatchAsyncBroadcast(this, context, "Refresh(${appWidgetIds.joinToString(",")})") {
            WidgetActionExecutor.refreshWidgetIds(
                context = context,
                appWidgetIds = appWidgetIds,
                repositoryOverride = repositoryProviderForTests?.invoke(context)
            )
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

        internal fun dispatchAsyncBroadcast(
            receiver: BroadcastReceiver,
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
            val pendingResult = receiver.goAsync()
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

        internal fun cancelAndJoinPendingWorkForTests() {
            val pendingJobs = widgetScope.coroutineContext[Job]?.children?.toList().orEmpty()
            if (pendingJobs.isEmpty()) return
            runBlocking {
                pendingJobs.forEach { it.cancelAndJoin() }
            }
        }
    }
}
