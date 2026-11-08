package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.WeMoRuleEntity
import com.example.ui.theme.WemoBackgroundGray
import com.example.ui.theme.WemoDividerGray
import com.example.ui.theme.WemoGreenDark
import com.example.ui.theme.WemoGreenPrimary
import com.example.ui.theme.WemoGreenSoft
import com.example.ui.theme.WemoRowSurface
import com.example.ui.theme.WemoTextPrimary
import com.example.ui.theme.WemoTextSecondary
import com.example.ui.theme.WorkSansFontFamily

@Composable
fun WeMoRulesScreen(
    rules: List<WeMoRuleEntity>,
    onToggleRule: (WeMoRuleEntity) -> Unit,
    onRunRuleNow: (WeMoRuleEntity) -> Unit,
    onDeleteRule: (WeMoRuleEntity) -> Unit,
    onAddRuleClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(WemoBackgroundGray)
            .testTag("rules_list"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Surface(
                color = WemoRowSurface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Switch Rules & Timers",
                            color = WemoTextPrimary,
                            fontFamily = WorkSansFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Executes directly over LAN without cloud servers",
                            color = WemoTextSecondary,
                            fontFamily = WorkSansFontFamily,
                            fontSize = 13.sp
                        )
                    }
                    Button(
                        onClick = onAddRuleClick,
                        colors = ButtonDefaults.buttonColors(containerColor = WemoGreenPrimary),
                        modifier = Modifier.testTag("add_rule_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Rule")
                    }
                }
            }
            HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
        }

        items(rules, key = { it.id }) { rule ->
            Surface(
                color = WemoRowSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rule_row_${rule.id}")
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (rule.isEnabled) WemoGreenSoft else Color(0xFFECEFF2),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = when (rule.ruleType) {
                                        "MOTION_TRIGGER" -> Icons.Default.Sensors
                                        "AUTO_OFF" -> Icons.Default.Timer
                                        else -> Icons.Default.Schedule
                                    },
                                    contentDescription = null,
                                    tint = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = rule.name,
                                color = WemoTextPrimary,
                                fontFamily = WorkSansFontFamily,
                                fontWeight = FontWeight.Normal,
                                fontSize = 18.sp
                            )
                            Text(
                                text = rule.triggerSummary,
                                color = WemoTextSecondary,
                                fontFamily = WorkSansFontFamily,
                                fontSize = 14.sp
                            )
                            Text(
                                text = rule.actionSummary,
                                color = if (rule.isEnabled) WemoGreenDark else WemoTextSecondary,
                                fontFamily = WorkSansFontFamily,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        IconButton(
                            onClick = { onRunRuleNow(rule) },
                            modifier = Modifier.testTag("run_rule_${rule.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Test rule ${rule.name} now",
                                tint = WemoGreenPrimary
                            )
                        }

                        Switch(
                            checked = rule.isEnabled,
                            onCheckedChange = { onToggleRule(rule) },
                            colors = SwitchDefaults.colors(checkedTrackColor = WemoGreenPrimary),
                            modifier = Modifier.testTag("toggle_rule_${rule.id}")
                        )

                        IconButton(
                            onClick = { onDeleteRule(rule) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete rule ${rule.name}",
                                tint = WemoTextSecondary
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp, color = WemoDividerGray)
                }
            }
        }
    }
}
