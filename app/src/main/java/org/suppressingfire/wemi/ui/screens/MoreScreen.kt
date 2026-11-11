package org.suppressingfire.wemi.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.data.SoapLogEntry
import org.suppressingfire.wemi.ui.theme.WemoBackgroundGray
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoRowSurface
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

@Composable
fun MoreScreen(
    devices: List<DeviceEntity>,
    soapLogs: List<SoapLogEntry>,
    useWeMiWordmark: Boolean,
    isDemoShowroomMode: Boolean = false,
    onTurnAllOn: () -> Unit,
    onTurnAllOff: () -> Unit,
    onScanNetwork: () -> Unit,
    onResetClassicDevices: () -> Unit,
    onResetLanHomesAndSwitches: () -> Unit = {},
    onToggleDemoShowroomMode: (Boolean) -> Unit = {},
    onOpenProvisionApWizard: () -> Unit = {},
    onToggleBrandWordmark: () -> Unit,
    onClearLogs: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WemoBackgroundGray)
            .testTag("more_screen_list"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "more_master_switch_control") {
            MasterSwitchControlCard(
                devices = devices,
                onTurnAllOn = onTurnAllOn,
                onTurnAllOff = onTurnAllOff
            )
        }

        item(key = "more_local_upnp_engine") {
            LocalUpnpEngineCard(
                isDemoShowroomMode = isDemoShowroomMode,
                useWeMiWordmark = useWeMiWordmark,
                onToggleDemoShowroomMode = onToggleDemoShowroomMode,
                onToggleBrandWordmark = onToggleBrandWordmark,
                onScanNetwork = onScanNetwork,
                onResetClassicDevices = onResetClassicDevices,
                onResetLanHomesAndSwitches = onResetLanHomesAndSwitches,
                onOpenProvisionApWizard = onOpenProvisionApWizard
            )
        }

        item(key = "more_play_store_assets") {
            PlayStoreAssetsCard()
        }

        item(key = "more_soap_packet_log") {
            SoapPacketLogCard(
                devices = devices,
                soapLogs = soapLogs,
                isDemoShowroomMode = isDemoShowroomMode,
                onClearLogs = onClearLogs
            )
        }

        item(key = "more_open_source_license") {
            OpenSourceLicenseCard()
        }
    }
}

@Composable
private fun OpenSourceLicenseCard() {
    var showFullLicenseText by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = WemoRowSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("open_source_licenses_card")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Open Source License",
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 16.sp,
                        color = WemoTextPrimary
                    )
                    Text(
                        text = "MIT License • Copyright (c) 2026 Michael R. Head & Contributors",
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        color = WemoTextSecondary
                    )
                }
                TextButton(
                    onClick = { showFullLicenseText = !showFullLicenseText },
                    modifier = Modifier.testTag("toggle_license_details_button")
                ) {
                    Text(
                        text = if (showFullLicenseText) "Hide" else "View License",
                        color = WemoGreenDark,
                        fontSize = 12.sp
                    )
                }
            }

            if (showFullLicenseText) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MoreCodeSurfaceGray,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("mit_license_text_surface")
                ) {
                    Text(
                        text = MIT_LICENSE_TEXT,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = WemoTextSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MoreCodeSurfaceGray,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("third_party_notices_surface")
                ) {
                    Text(
                        text = THIRD_PARTY_NOTICES_TEXT,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = WemoTextSecondary,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }
    }
}

private val MIT_LICENSE_TEXT = """
    MIT License
    Copyright (c) 2026 Michael R. Head and WeMi Light Control Contributors

    Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

    The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

    THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
""".trimIndent()

private val THIRD_PARTY_NOTICES_TEXT = """
    Third-Party Open Source Notices:
    • AndroidX, Jetpack Compose & Room — Apache License 2.0 (Copyright The Android Open Source Project)
    • Kotlin & Kotlinx Coroutines — Apache License 2.0 (Copyright JetBrains s.r.o.)
    • Trademark Notice: WeMi Light Control is an independent open-source project and is not affiliated with or endorsed by Belkin International, Inc.
""".trimIndent()
