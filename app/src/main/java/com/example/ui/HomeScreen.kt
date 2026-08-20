package com.example.ui

import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Surface
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import com.example.wake.WakePrefsManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit = {},
    onOpenFavorites: () -> Unit = {}
) {
    val context = LocalContext.current
    val wakeState by WakePrefsManager.wakeState.collectAsStateWithLifecycle()
    val isPreviewMissedDay = wakeState.previewMissedDay
    val prayerHistory = remember(context, wakeState) { WakePrefsManager.getPrayerHistory(context) }
    val streakCount = remember(prayerHistory) { WakePrefsManager.calculateStreak(prayerHistory) }
    val firstInstallDate = remember(context) { WakePrefsManager.getFirstInstallDate(context) }

    // Calendar state for month navigation
    val todayCal = remember { Calendar.getInstance() }
    val todayYear = remember { todayCal.get(Calendar.YEAR) }
    val todayMonth = remember { todayCal.get(Calendar.MONTH) } // 0-indexed
    val todayDay = remember { todayCal.get(Calendar.DAY_OF_MONTH) }

    var displayYear by remember { mutableIntStateOf(todayYear) }
    var displayMonth by remember { mutableIntStateOf(todayMonth) }

    val monthCal = remember(displayYear, displayMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val monthNameCaps = remember(monthCal) {
        SimpleDateFormat("MMMM", Locale.US).format(monthCal.time).uppercase(Locale.US)
    }

    val todayWeekdayShort = remember(todayCal) {
        SimpleDateFormat("EEE", Locale.US).format(todayCal.time)
    }

    // Is reduced motion enabled?
    val isReducedMotion = remember(context) {
        try {
            val durationScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
            val transitionScale = Settings.Global.getFloat(context.contentResolver, Settings.Global.TRANSITION_ANIMATION_SCALE, 1f)
            durationScale == 0f || transitionScale == 0f
        } catch (e: Exception) {
            false
        }
    }

    // PART 4 — FONT SCALE RESILIENCE: Cap fontScale at max 1.15f
    val currentDensity = LocalDensity.current
    val cappedDensity = remember(currentDensity) {
        Density(
            density = currentDensity.density,
            fontScale = minOf(currentDensity.fontScale, 1.15f)
        )
    }

    val scrollState = rememberScrollState()
    val statusBarPaddingTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPaddingBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    CompositionLocalProvider(LocalDensity provides cappedDensity) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF2EFE6))
        ) {
            // Main Non-Scrolling Outer Column
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        top = statusBarPaddingTop + 12.dp,
                        start = 24.dp,
                        end = 24.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // --- STATIC TOP SECTION ---
                // 1. Flame Badge & Settings Gear
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(40.dp)
                            .testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "Settings",
                            tint = Color(0xFF1C1714),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    StreakFireBadge(
                        streakNumber = streakCount,
                        isReducedMotion = isReducedMotion,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. PRAY NOW SECTION (CONDITIONAL - shown when today's prayer is pending OR force "Pray Now" simulation is ON in dev options)
                val todayIso = String.format(Locale.US, "%04d-%02d-%02d", todayYear, todayMonth + 1, todayDay)
                val isTodayCompleted = prayerHistory.contains(todayIso)
                val isPreviewPrayNow = wakeState.previewPrayNow

                if (!isTodayCompleted || isPreviewPrayNow) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    ) {
                        Text(
                            text = "Today's prayer is still waiting.",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF8B7E72),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Surface(
                            onClick = {
                                com.example.wake.WakeOverlayManager.triggerPrayerDoor(context)
                            },
                            shape = CircleShape,
                            color = Color(0xFFB4574E),
                            modifier = Modifier
                                .shadow(
                                    elevation = 8.dp,
                                    shape = CircleShape,
                                    ambientColor = Color(0x59B4574E),
                                    spotColor = Color(0x59B4574E)
                                )
                                .testTag("pray_now_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 28.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_streak_flame),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Pray Now",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                // 3. MONTH HEADER & CHEVRONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = monthNameCaps,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF1C1714),
                            letterSpacing = 1.2.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = displayYear.toString(),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Normal,
                            color = Color(0xFF8B7E72),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = todayWeekdayShort,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF1C1714),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Navigation Chevrons ‹ ›
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "Previous Month",
                            tint = Color(0xFF1C1714),
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (displayMonth == 0) {
                                        displayMonth = 11
                                        displayYear -= 1
                                    } else {
                                        displayMonth -= 1
                                    }
                                }
                        )

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Next Month",
                            tint = Color(0xFF1C1714),
                            modifier = Modifier
                                .size(24.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) {
                                    if (displayMonth == 11) {
                                        displayMonth = 0
                                        displayYear += 1
                                    } else {
                                        displayMonth += 1
                                    }
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. WEEKDAY HEADER ROW (M T W T F S S)
                val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    weekdays.forEach { dayLetter ->
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayLetter,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF8B7E72),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // --- DYNAMIC SCROLLABLE PORTION (ONLY THE CALENDAR NUMBERS GRID) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier.padding(bottom = navBarPaddingBottom + 96.dp)
                    ) {
                        CalendarGrid(
                            displayYear = displayYear,
                            displayMonth = displayMonth,
                            todayYear = todayYear,
                            todayMonth = todayMonth,
                            todayDay = todayDay,
                            prayerHistory = prayerHistory,
                            firstInstallDate = firstInstallDate,
                            isPreviewMissedDay = isPreviewMissedDay
                        )
                    }
                }
            }

            // Gradient Scrim behind Nav Pill
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xFFF2EFE6).copy(alpha = 0.90f)
                            )
                        )
                    )
            )

            // Bottom Navigation Pill (Fixed floating at bottom)
            BottomNavPill(
                activeTab = "home",
                onTabSelected = { tab ->
                    if (tab == "profile") {
                        onOpenProfile()
                    } else if (tab == "favorites") {
                        onOpenFavorites()
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        bottom = navBarPaddingBottom + 16.dp
                    )
            )
        }
    }
}

@Composable
fun StreakFireBadge(
    streakNumber: Int,
    isReducedMotion: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flicker_transition")

    val scaleX by if (!isReducedMotion) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 0.96f,
            animationSpec = infiniteRepeatable(
                animation = tween(400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale_x"
        )
    } else remember { androidx.compose.runtime.mutableFloatStateOf(1f) }

    val scaleY by if (!isReducedMotion) {
        infiniteTransition.animateFloat(
            initialValue = 1f,
            targetValue = 1.05f,
            animationSpec = infiniteRepeatable(
                animation = tween(350, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "scale_y"
        )
    } else remember { androidx.compose.runtime.mutableFloatStateOf(1f) }

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val flameWidth = minOf(maxWidth * 0.45f, 140.dp)
        val flameHeight = minOf(flameWidth * 0.88f, 125.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Flame Vector Icon with flicker animation
            Icon(
                painter = painterResource(id = R.drawable.ic_streak_flame),
                contentDescription = "Streak Flame",
                tint = Color.Unspecified,
                modifier = Modifier
                    .size(width = flameWidth, height = flameHeight)
                    .graphicsLayer(
                        scaleX = scaleX,
                        scaleY = scaleY,
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    )
            )

            Spacer(modifier = Modifier.height(2.dp))

            // Big streak number - stays BOLD (700) near-black #1C1714 (centered under flame)
            Text(
                text = streakNumber.toString(),
                fontSize = 60.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF1C1714),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            // "Daily streak" caption in #2C2420
            Text(
                text = "Daily streak",
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF2C2420),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    displayYear: Int,
    displayMonth: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    prayerHistory: Set<String>,
    firstInstallDate: String,
    isPreviewMissedDay: Boolean
) {
    val sdf = remember { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US) }
    val todayIso = String.format(Locale.US, "%04d-%02d-%02d", todayYear, todayMonth + 1, todayDay)

    val calYesterday = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
    }
    val yesterdayIso = sdf.format(calYesterday.time)

    // Parse install date (e.g. "2026-08-10")
    val (installYear, installMonth, installDay) = remember(firstInstallDate) {
        try {
            val parts = firstInstallDate.split("-")
            Triple(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        } catch (e: Exception) {
            Triple(todayYear, todayMonth, todayDay)
        }
    }

    val cal = remember(displayYear, displayMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val maxDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Determine start day for this month:
    // Days before install date are NOT in the grid at all. The grid begins from install day.
    val startDay = when {
        displayYear < installYear || (displayYear == installYear && displayMonth < installMonth) -> {
            Int.MAX_VALUE // Past month before install -> no days
        }
        displayYear == installYear && displayMonth == installMonth -> {
            installDay // Install month -> start from install day (e.g. 10 or 20)
        }
        else -> {
            1 // Future month after install -> start at day 1
        }
    }

    if (startDay > maxDays) {
        // No days in this month
        return
    }

    // Determine weekday offset for startDay on Row 0 (Monday = 0)
    val startOffset = remember(displayYear, displayMonth, startDay) {
        val calStart = Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, startDay)
        }
        val firstDayOfWeek = calStart.get(Calendar.DAY_OF_WEEK) // SUNDAY=1, MONDAY=2...
        (firstDayOfWeek + 5) % 7 // Monday = 0, Tuesday = 1...
    }

    val totalVisibleDays = maxDays - startDay + 1
    val totalCells = startOffset + totalVisibleDays
    val numRows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        for (rowIndex in 0 until numRows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (colIndex in 0 until 7) {
                    val cellIndex = rowIndex * 7 + colIndex
                    val dayNumber = startDay + (cellIndex - startOffset)

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cellIndex >= startOffset && dayNumber <= maxDays) {
                            val dayIso = String.format(Locale.US, "%04d-%02d-%02d", displayYear, displayMonth + 1, dayNumber)
                            val realCompleted = prayerHistory.contains(dayIso)

                            val isMissed = if (isPreviewMissedDay) {
                                if (yesterdayIso >= firstInstallDate) {
                                    dayIso == yesterdayIso
                                } else {
                                    dayNumber == startDay
                                }
                            } else {
                                !realCompleted && dayIso < todayIso
                            }

                            val isCompleted = if (isPreviewMissedDay) {
                                if (yesterdayIso >= firstInstallDate) {
                                    if (dayIso == yesterdayIso) false else realCompleted
                                } else {
                                    if (dayNumber == startDay) false else realCompleted
                                }
                            } else {
                                realCompleted
                            }

                            DayCircle(
                                dayNumber = dayNumber,
                                isCompleted = isCompleted,
                                isMissed = isMissed
                            )
                        } else {
                            Spacer(modifier = Modifier.size(40.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCircle(
    dayNumber: Int,
    isCompleted: Boolean,
    isMissed: Boolean
) {
    val backgroundColor = if (isCompleted) Color(0xFFB4574E) else Color(0xFF1C1714)

    Box(
        modifier = Modifier
            .size(40.dp)
            .background(backgroundColor, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (isMissed) {
            Canvas(modifier = Modifier.size(16.dp)) {
                val strokeWidthPx = 2.5.dp.toPx()
                val color = Color(0xFFFDFCF8)
                drawLine(
                    color = color,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = color,
                    start = Offset(size.width, 0f),
                    end = Offset(0f, size.height),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            }
        } else {
            Text(
                text = dayNumber.toString(),
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFFFDFCF8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun BottomNavPill(
    activeTab: String = "home",
    onTabSelected: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .wrapContentWidth()
            .height(64.dp)
            .shadow(
                elevation = 12.dp,
                shape = CircleShape,
                ambientColor = Color(0x1A2C2420),
                spotColor = Color(0x1A2C2420)
            )
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.60f),
                shape = CircleShape
            )
            .clip(CircleShape)
            .background(Color(0xFFFDFCF8).copy(alpha = 0.75f))
            .padding(horizontal = 28.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
        ) {
            // Home Item
            val isHomeActive = activeTab == "home"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isHomeActive) Modifier.background(Color(0xFFB4574E).copy(alpha = 0.12f))
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTabSelected("home")
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Home,
                        contentDescription = "Home",
                        tint = if (isHomeActive) Color(0xFFB4574E) else Color(0xFF1C1714).copy(alpha = 0.70f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Home",
                        fontSize = 11.sp,
                        fontWeight = if (isHomeActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isHomeActive) Color(0xFFB4574E) else Color(0xFF1C1714).copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Favorites Item (clickable)
            val isFavoritesActive = activeTab == "favorites"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isFavoritesActive) Modifier.background(Color(0xFFB4574E).copy(alpha = 0.12f))
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTabSelected("favorites")
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "Favorites",
                        tint = if (isFavoritesActive) Color(0xFFB4574E) else Color(0xFF1C1714).copy(alpha = 0.70f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Favorites",
                        fontSize = 11.sp,
                        fontWeight = if (isFavoritesActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isFavoritesActive) Color(0xFFB4574E) else Color(0xFF1C1714).copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Profile Item (clickable)
            val isProfileActive = activeTab == "profile"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .then(
                        if (isProfileActive) Modifier.background(Color(0xFFB4574E).copy(alpha = 0.12f))
                        else Modifier
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onTabSelected("profile")
                    }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = "Profile",
                        tint = if (isProfileActive) Color(0xFFB4574E) else Color(0xFF1C1714).copy(alpha = 0.70f),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Profile",
                        fontSize = 11.sp,
                        fontWeight = if (isProfileActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isProfileActive) Color(0xFFB4574E) else Color(0xFF1C1714).copy(alpha = 0.70f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
