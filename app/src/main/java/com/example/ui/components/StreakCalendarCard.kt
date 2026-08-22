package com.example.ui.components

/**
 * StreakCalendarCard
 *
 * Isolated calendar container component displaying:
 * - Month header with navigation chevrons (‹ ›) and current weekday indicator
 * - Weekday header row (M T W T F S S)
 * - Restyled liquid-glass lollipop day grid:
 *   • Prayed days: glossy terracotta vertical gradient with white shine highlight, rim light, and warm shadow
 *   • Missed days: glossy deep warm brown balloon with cream cancelled 'X'
 *   • Active unprayed days: glossy deep warm brown balloon with cream date numbers
 *   • Today: thin terracotta highlight ring indicator
 *   • Pre-install days: pale flat warm gray-cream circle with dimmed numbers (days 1..31 always visible)
 *
 * Wrapped in an elevated warm-white card (radius 24dp, soft shadow, subtle top rim shine).
 */

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class CalendarDayState {
    PRE_INSTALL,
    PRAYED,
    MISSED,
    ACTIVE_UNPRAYED
}

@Composable
fun StreakCalendarCard(
    displayYear: Int,
    displayMonth: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    prayerHistory: Set<String>,
    firstInstallDate: String,
    isPreviewMissedDay: Boolean = false,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    modifier: Modifier = Modifier
) {
    val monthCal = remember(displayYear, displayMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }

    val todayCal = remember { Calendar.getInstance() }

    val monthNameCaps = remember(monthCal) {
        SimpleDateFormat("MMMM", Locale.US).format(monthCal.time).uppercase(Locale.US)
    }

    val todayWeekdayShort = remember(todayCal) {
        SimpleDateFormat("EEE", Locale.US).format(todayCal.time)
    }

    val weekdays = listOf("M", "T", "W", "T", "F", "S", "S")

    // Calendar Card Container
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFCF8)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.70f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 9.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = Color(0x1F2C2420),
                spotColor = Color(0x242C2420)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            // 1. MONTH HEADER & CHEVRONS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = monthNameCaps,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1C1714),
                        letterSpacing = 1.2.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displayYear.toString(),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF8B7E72),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = todayWeekdayShort,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1C1714),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                            ) { onPreviousMonth() }
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
                            ) { onNextMonth() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 2. WEEKDAY HEADER ROW (M T W T F S S)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
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

            // 3. DAYS GRID
            CalendarDaysGrid(
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

@Composable
private fun CalendarDaysGrid(
    displayYear: Int,
    displayMonth: Int,
    todayYear: Int,
    todayMonth: Int,
    todayDay: Int,
    prayerHistory: Set<String>,
    firstInstallDate: String,
    isPreviewMissedDay: Boolean
) {
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US) }
    val todayIso = String.format(Locale.US, "%04d-%02d-%02d", todayYear, todayMonth + 1, todayDay)

    val calYesterday = remember {
        Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -1)
        }
    }
    val yesterdayIso = sdf.format(calYesterday.time)

    val calStart = remember(displayYear, displayMonth) {
        Calendar.getInstance().apply {
            set(Calendar.YEAR, displayYear)
            set(Calendar.MONTH, displayMonth)
            set(Calendar.DAY_OF_MONTH, 1)
        }
    }
    val maxDays = calStart.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Weekday offset for day 1 (Sunday=1 -> 6, Monday=2 -> 0, Tuesday=3 -> 1, ...)
    val startOffset = remember(displayYear, displayMonth) {
        val firstDayOfWeek = calStart.get(Calendar.DAY_OF_WEEK)
        (firstDayOfWeek + 5) % 7
    }

    val totalCells = startOffset + maxDays
    val numRows = (totalCells + 6) / 7

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (rowIndex in 0 until numRows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                for (colIndex in 0 until 7) {
                    val cellIndex = rowIndex * 7 + colIndex
                    val dayNumber = cellIndex - startOffset + 1

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (cellIndex >= startOffset && dayNumber <= maxDays) {
                            val dayIso = String.format(Locale.US, "%04d-%02d-%02d", displayYear, displayMonth + 1, dayNumber)
                            val isToday = (displayYear == todayYear && displayMonth == todayMonth && dayNumber == todayDay)
                            val isPreInstall = dayIso < firstInstallDate

                            val dayState = when {
                                isPreInstall -> CalendarDayState.PRE_INSTALL
                                else -> {
                                    val realCompleted = prayerHistory.contains(dayIso)
                                    val isMissed = if (isPreviewMissedDay) {
                                        if (yesterdayIso >= firstInstallDate) {
                                            dayIso == yesterdayIso
                                        } else {
                                            dayIso == firstInstallDate
                                        }
                                    } else {
                                        !realCompleted && dayIso < todayIso
                                    }

                                    val isCompleted = if (isPreviewMissedDay) {
                                        if (yesterdayIso >= firstInstallDate) {
                                            if (dayIso == yesterdayIso) false else realCompleted
                                        } else {
                                            if (dayIso == firstInstallDate) false else realCompleted
                                        }
                                    } else {
                                        realCompleted
                                    }

                                    when {
                                        isCompleted -> CalendarDayState.PRAYED
                                        isMissed -> CalendarDayState.MISSED
                                        else -> CalendarDayState.ACTIVE_UNPRAYED
                                    }
                                }
                            }

                            DayCircleItem(
                                dayNumber = dayNumber,
                                state = dayState,
                                isToday = isToday
                            )
                        } else {
                            Spacer(modifier = Modifier.size(38.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCircleItem(
    dayNumber: Int,
    state: CalendarDayState,
    isToday: Boolean,
    modifier: Modifier = Modifier
) {
    val circleSize = 38.dp

    Box(
        modifier = modifier
            .size(circleSize)
            .then(
                if (isToday) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color(0xFFB4574E),
                        shape = CircleShape
                    )
                } else Modifier
            )
            .padding(if (isToday) 2.dp else 0.dp)
            .then(
                if (state != CalendarDayState.PRE_INSTALL) {
                    Modifier.shadow(
                        elevation = 3.dp,
                        shape = CircleShape,
                        ambientColor = if (state == CalendarDayState.PRAYED) Color(0x3DB4574E) else Color(0x332C2420),
                        spotColor = if (state == CalendarDayState.PRAYED) Color(0x3DB4574E) else Color(0x332C2420)
                    )
                } else Modifier
            )
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val radius = canvasWidth / 2f

            when (state) {
                CalendarDayState.PRE_INSTALL -> {
                    // Pale flat warm gray-cream circle (NO shine, NO shadow)
                    drawCircle(
                        color = Color(0xFFE8E0D4).copy(alpha = 0.55f),
                        radius = radius
                    )
                }
                CalendarDayState.PRAYED -> {
                    // Glossy terracotta vertical gradient balloon
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFFC9706A), Color(0xFF9E463F))
                        ),
                        radius = radius
                    )
                    // White shine highlight on top ~45%
                    drawOval(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.45f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        topLeft = Offset(canvasWidth * 0.18f, canvasHeight * 0.08f),
                        size = Size(canvasWidth * 0.64f, canvasHeight * 0.38f)
                    )
                    // Rim light
                    drawCircle(
                        color = Color.White.copy(alpha = 0.30f),
                        radius = radius - 0.5.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                CalendarDayState.MISSED -> {
                    // Glossy deep warm brown balloon
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF5A362F), Color(0xFF381F1B))
                        ),
                        radius = radius
                    )
                    // White shine highlight on top ~45%
                    drawOval(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        topLeft = Offset(canvasWidth * 0.18f, canvasHeight * 0.08f),
                        size = Size(canvasWidth * 0.64f, canvasHeight * 0.38f)
                    )
                    // Rim light
                    drawCircle(
                        color = Color.White.copy(alpha = 0.22f),
                        radius = radius - 0.5.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
                CalendarDayState.ACTIVE_UNPRAYED -> {
                    // Glossy deep warm brown balloon (replaces flat black circles)
                    drawCircle(
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF4A3831), Color(0xFF281E1A))
                        ),
                        radius = radius
                    )
                    // White shine highlight on top ~45%
                    drawOval(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.35f),
                                Color.White.copy(alpha = 0.05f)
                            )
                        ),
                        topLeft = Offset(canvasWidth * 0.18f, canvasHeight * 0.08f),
                        size = Size(canvasWidth * 0.64f, canvasHeight * 0.38f)
                    )
                    // Rim light
                    drawCircle(
                        color = Color.White.copy(alpha = 0.22f),
                        radius = radius - 0.5.dp.toPx(),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }
        }

        if (state == CalendarDayState.MISSED) {
            // Cancelled X in cream
            Canvas(modifier = Modifier.size(14.dp)) {
                val strokeWidthPx = 2.4.dp.toPx()
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
                fontSize = 15.sp,
                fontWeight = if (state == CalendarDayState.PRE_INSTALL) FontWeight.Normal else FontWeight.Medium,
                color = if (state == CalendarDayState.PRE_INSTALL) Color(0xFF8B7E72).copy(alpha = 0.50f) else Color(0xFFFDFCF8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
