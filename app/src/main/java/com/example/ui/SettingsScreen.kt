package com.example.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.wake.WakePrefsManager

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenCustomization: () -> Unit,
    onOpenDiagnostic: () -> Unit
) {
    val context = LocalContext.current
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var isDevNotifTestEnabled by remember {
        mutableStateOf(WakePrefsManager.isDevNotificationTestEnabled(context))
    }

    BackHandler {
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2EFE6))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 48.dp)
        ) {
            // Top Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color(0xFF1C1714),
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.padding(horizontal = 8.dp))
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1C1714)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Entry 1: "Customization"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF7F4EC))
                        .clickable { onOpenCustomization() }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Customization",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1C1714)
                        )
                        Text(
                            text = "Lock screen memory verse & preferences",
                            fontSize = 12.sp,
                            color = Color(0xFF8B7E72)
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF8B7E72),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Entry 2: "Developer Tools" (existing 7-tap unlock)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF7F4EC))
                        .clickable {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime > 2000L) {
                                tapCount = 1
                            } else {
                                tapCount++
                            }
                            lastTapTime = now

                            val remaining = 7 - tapCount
                            if (tapCount in 3..6) {
                                Toast
                                    .makeText(
                                        context,
                                        "$remaining more taps",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            } else if (tapCount >= 7) {
                                tapCount = 0
                                onOpenDiagnostic()
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Developer Tools",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1C1714)
                        )
                        Text(
                            text = "System diagnostics & developer controls (7 taps)",
                            fontSize = 12.sp,
                            color = Color(0xFF8B7E72)
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = Color(0xFF8B7E72),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Entry 3: "Developer Option — Notification Testing"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF7F4EC))
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developer Notification Testing",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1C1714)
                        )
                        Text(
                            text = "Show instant notification test button in Profile",
                            fontSize = 12.sp,
                            color = Color(0xFF8B7E72)
                        )
                    }

                    Switch(
                        checked = isDevNotifTestEnabled,
                        onCheckedChange = { enabled ->
                            isDevNotifTestEnabled = enabled
                            WakePrefsManager.setDevNotificationTestEnabled(context, enabled)
                            Toast.makeText(
                                context,
                                if (enabled) "Test notification button added to Profile" else "Test notification button removed from Profile",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFFFDFCF8),
                            checkedTrackColor = Color(0xFF059669),
                            checkedBorderColor = Color(0xFF059669),
                            uncheckedThumbColor = Color(0xFF8B7E72),
                            uncheckedTrackColor = Color(0xFFE8E0D4),
                            uncheckedBorderColor = Color(0xFFE8E0D4)
                        ),
                        modifier = Modifier.testTag("settings_dev_notification_switch")
                    )
                }
            }
        }
    }
}
