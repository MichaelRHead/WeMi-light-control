package org.suppressingfire.wemi.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.suppressingfire.wemi.R
import org.suppressingfire.wemi.data.DeviceEntity
import org.suppressingfire.wemi.network.HardwareLocationInfo
import org.suppressingfire.wemi.network.HardwareRule
import org.suppressingfire.wemi.network.HardwareRulesBackupEntry
import org.suppressingfire.wemi.network.HardwareRulesDbMutator
import org.suppressingfire.wemi.ui.theme.WemoDividerGray
import org.suppressingfire.wemi.ui.theme.WemoGreenDark
import org.suppressingfire.wemi.ui.theme.WemoGreenSoft
import org.suppressingfire.wemi.ui.theme.WemoTextPrimary
import org.suppressingfire.wemi.ui.theme.WemoTextSecondary

/**
 * Zero-permission, offline Solar Location editor updating `LOCATIONINFO` inside a switch's
 * `rules.db` so on-device Sunrise/Sunset schedules calculate accurate local twilight times.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HardwareLocationDialog(
    device: DeviceEntity,
    initialLocation: HardwareLocationInfo?,
    onDismiss: () -> Unit,
    onSaveLocation: (HardwareLocationInfo) -> Unit
) {
    var cityName by rememberSaveable(device.id) { mutableStateOf(initialLocation?.cityName.orEmpty()) }
    var region by rememberSaveable(device.id) { mutableStateOf(initialLocation?.region.orEmpty()) }
    var countryName by rememberSaveable(device.id) { mutableStateOf(initialLocation?.countryName?.ifBlank { "United States" } ?: "United States") }
    var latitude by rememberSaveable(device.id) { mutableStateOf(initialLocation?.latitude.orEmpty()) }
    var longitude by rememberSaveable(device.id) { mutableStateOf(initialLocation?.longitude.orEmpty()) }
    var errorText by rememberSaveable(device.id) { mutableStateOf<String?>(null) }

    val parsedLatLive = latitude.trim().toDoubleOrNull()
    val parsedLonLive = longitude.trim().toDoubleOrNull()
    val isLatError = errorText != null && (parsedLatLive == null || parsedLatLive !in -90.0..90.0)
    val isLonError = errorText != null && (parsedLonLive == null || parsedLonLive !in -180.0..180.0)

    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = WemoGreenSoft,
        selectedLabelColor = WemoGreenDark
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Solar Location • ${device.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Used by the switch's offline Sunrise/Sunset calculator. No phone location permission needed.",
                        color = WemoTextSecondary,
                        fontSize = 12.sp
                    )
                    Text("Quick City Presets", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HardwareRulesDbMutator.PRESET_SOLAR_LOCATIONS.forEach { preset ->
                            FilterChip(
                                selected = cityName.equals(preset.cityName, ignoreCase = true) && latitude == preset.latitude,
                                onClick = {
                                    cityName = preset.cityName
                                    region = preset.region
                                    countryName = preset.countryName
                                    latitude = preset.latitude
                                    longitude = preset.longitude
                                    errorText = null
                                },
                                label = { Text(preset.cityName, fontSize = 11.5.sp) },
                                colors = chipColors,
                                modifier = Modifier.testTag("solar_preset_${preset.cityName.lowercase().replace(' ', '_')}")
                            )
                        }
                    }
                    OutlinedTextField(
                        value = cityName,
                        onValueChange = { cityName = it; errorText = null },
                        label = { Text("City Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("solar_city_input")
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        if (maxWidth < 300.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = region,
                                    onValueChange = { region = it; errorText = null },
                                    label = { Text("State / Region") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = countryName,
                                    onValueChange = { countryName = it; errorText = null },
                                    label = { Text("Country") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedTextField(
                                    value = region,
                                    onValueChange = { region = it; errorText = null },
                                    label = { Text("State / Region") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = countryName,
                                    onValueChange = { countryName = it; errorText = null },
                                    label = { Text("Country") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = latitude,
                            onValueChange = { raw ->
                                latitude = raw.filter { it.isDigit() || it == '.' || it == '-' }.take(12)
                                errorText = null
                            },
                            label = { Text("Latitude") },
                            isError = isLatError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("solar_lat_input")
                        )
                        OutlinedTextField(
                            value = longitude,
                            onValueChange = { raw ->
                                longitude = raw.filter { it.isDigit() || it == '.' || it == '-' }.take(12)
                                errorText = null
                            },
                            label = { Text("Longitude") },
                            isError = isLonError,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("solar_lon_input")
                        )
                    }
                }
                errorText?.let { err ->
                    Text(
                        text = err,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .semantics { liveRegion = LiveRegionMode.Assertive }
                            .testTag("solar_location_validation_error")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lat = latitude.trim().toDoubleOrNull()
                    val lon = longitude.trim().toDoubleOrNull()
                    if (lat == null || lat !in -90.0..90.0) {
                        errorText = "Enter a valid latitude (-90 to 90)"
                        return@Button
                    }
                    if (lon == null || lon !in -180.0..180.0) {
                        errorText = "Enter a valid longitude (-180 to 180)"
                        return@Button
                    }
                    onSaveLocation(
                        HardwareLocationInfo(
                            cityName = cityName.trim().ifBlank { "Custom Location" },
                            region = region.trim(),
                            countryName = countryName.trim().ifBlank { "United States" },
                            latitude = latitude.trim(),
                            longitude = longitude.trim()
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = WemoGreenDark),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("save_solar_location_button")
            ) {
                Text("Save Location")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

/**
 * Backup restore selector listing retained raw `/rules.db` ZIP archives for [device].
 */
@Composable
fun HardwareRulesBackupDialog(
    device: DeviceEntity,
    backups: List<HardwareRulesBackupEntry>,
    onDismiss: () -> Unit,
    onRestoreBackup: (HardwareRulesBackupEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Restore Rules Backup • ${device.name}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "WeMi saves up to 5 untouched backups of your switch's rules archive before every write. Restoring backs up your current rules first, validates SQLite integrity, and increments DB version safely.",
                    color = WemoTextSecondary,
                    fontSize = 12.sp
                )
                if (backups.isEmpty()) {
                    Text(
                        text = "No backups saved for this switch yet. Read or modify its rules once to create an automatic backup.",
                        color = WemoTextSecondary,
                        fontSize = 12.5.sp
                    )
                } else {
                    backups.forEachIndexed { idx, entry ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, WemoDividerGray),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("backup_restore_item_$idx")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "DB v${entry.dbVersion} • ${entry.createdAtLabel}",
                                        color = WemoTextPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.sp
                                    )
                                    val kb = (entry.sizeBytes / 1024.0)
                                    Text(
                                        text = String.format(java.util.Locale.US, "%.1f KB raw archive", kb),
                                        color = WemoTextSecondary,
                                        fontSize = 11.sp
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = { onRestoreBackup(entry) },
                                    colors = ButtonDefaults.buttonColors(containerColor = WemoGreenDark),
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("restore_backup_button_$idx")
                                ) {
                                    Text("Restore", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("Close")
            }
        }
    )
}

/**
 * Confirmation dialog before removing a stored on-device rule from switch flash.
 */
@Composable
fun DeleteHardwareRuleConfirmDialog(
    device: DeviceEntity,
    rule: HardwareRule,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete On-Device Rule?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Remove '${rule.name}' (${rule.type.displayLabel}) from ${device.name}?",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "An untouched backup of the switch's current rules archive is saved automatically before writing.",
                    color = WemoTextSecondary,
                    fontSize = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(containerColor = StatusOfflineRed),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("confirm_delete_hardware_rule_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
