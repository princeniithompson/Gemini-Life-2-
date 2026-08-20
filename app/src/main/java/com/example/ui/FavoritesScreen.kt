package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.favorites.FavoritesManager
import com.example.favorites.SavedPrayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FavoritesScreen(
    onBack: () -> Unit = {},
    onOpenHome: () -> Unit = {},
    onOpenProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    var savedPrayers by remember { mutableStateOf<List<SavedPrayer>>(emptyList()) }
    var selectedPrayer by remember { mutableStateOf<SavedPrayer?>(null) }

    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun refreshPrayers() {
        savedPrayers = FavoritesManager.getSavedPrayers(context)
        Log.i("FavoritesScreen", "[FAVORITES] Screen opened. Loaded ${savedPrayers.size} prayers from prefs.")
    }

    LaunchedEffect(Unit) {
        refreshPrayers()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF2EFE6) // Cream background
    ) {
        if (selectedPrayer != null) {
            // DETAIL VIEW
            val prayer = selectedPrayer!!
            val formattedDate = remember(prayer.date) {
                SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(prayer.date))
            }

            fun formatPrayerForSharing(): String {
                val sb = StringBuilder()
                sb.append("First Light — Prayer ($formattedDate)\n\n")
                if (prayer.memoryVerse.isNotBlank()) {
                    sb.append("“${prayer.memoryVerse}”\n\n")
                }
                if (prayer.transcript.isNotBlank()) {
                    val cleaned = prayer.transcript
                        .replace("Gemini: ", "")
                        .replace("Gemini:", "")
                    sb.append(cleaned)
                }
                return sb.toString().trim()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topInset, bottom = bottomInset)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Bar with Back Arrow, Copy, Share, and Delete Action Icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { selectedPrayer = null }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color(0xFF2C2420)
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Copy button
                            IconButton(
                                onClick = {
                                    val textToCopy = formatPrayerForSharing()
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("First Light Prayer", textToCopy)
                                    clipboard.setPrimaryClip(clip)
                                    Toast.makeText(context, "Prayer copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy Prayer",
                                    tint = Color(0xFF2C2420)
                                )
                            }

                            // Share button
                            IconButton(
                                onClick = {
                                    val textToShare = formatPrayerForSharing()
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_TEXT, textToShare)
                                        type = "text/plain"
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "Share Prayer")
                                    context.startActivity(shareIntent)
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Share,
                                    contentDescription = "Share Prayer",
                                    tint = Color(0xFF2C2420)
                                )
                            }

                            // Delete button
                            IconButton(
                                onClick = {
                                    FavoritesManager.deletePrayer(context, prayer.id)
                                    Toast.makeText(context, "Prayer removed from Favorites", Toast.LENGTH_SHORT).show()
                                    selectedPrayer = null
                                    refreshPrayers()
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete Prayer",
                                    tint = Color(0xFFB4574E)
                                )
                            }
                        }
                    }

                    // Content Scrollable
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = formattedDate,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF8B7E72),
                            letterSpacing = 0.5.sp
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        if (prayer.memoryVerse.isNotBlank()) {
                            Text(
                                text = "“${prayer.memoryVerse}”",
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontFamily = FontFamily.Serif,
                                    fontStyle = FontStyle.Italic,
                                    color = Color(0xFF2C2420),
                                    lineHeight = 32.sp
                                )
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color(0xFFE8E0D4))
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                        }

                        Text(
                            text = "Prayer Session Transcript",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF8B7E72),
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        if (prayer.transcript.isBlank()) {
                            Text(
                                text = "No transcript recorded for this session.",
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    color = Color(0xFF2C2420),
                                    lineHeight = 24.sp
                                )
                            )
                        } else {
                            val paragraphs = prayer.transcript.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                            paragraphs.forEach { paragraph ->
                                if (paragraph.startsWith("You:")) {
                                    Text(
                                        text = paragraph,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontFamily = FontFamily.SansSerif,
                                            color = Color(0xFF8B7E72),
                                            lineHeight = 20.sp
                                        ),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                } else if (paragraph.startsWith("Carry this with you:") || paragraph.startsWith("Carry this with you")) {
                                    Text(
                                        text = paragraph,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.Serif,
                                            fontStyle = FontStyle.Italic,
                                            color = Color(0xFFB4574E),
                                            lineHeight = 24.sp
                                        ),
                                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                                    )
                                } else {
                                    val displayText = if (paragraph.startsWith("Gemini:")) paragraph.removePrefix("Gemini:").trimStart() else paragraph
                                    Text(
                                        text = displayText,
                                        style = MaterialTheme.typography.bodyLarge.copy(
                                            fontFamily = FontFamily.SansSerif,
                                            color = Color(0xFF2C2420),
                                            lineHeight = 24.sp
                                        ),
                                        modifier = Modifier.padding(bottom = 16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        } else {
            // MAIN FAVORITES LIST
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset)
                ) {
                    // Header: Centered serif title "Favorites" with brand sparkle icon
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_brand_sparkle),
                            contentDescription = "Brand Sparkle",
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 6.dp)
                        )

                        Text(
                            text = "Favorites",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2C2420)
                            )
                        )
                    }

                    if (savedPrayers.isEmpty()) {
                        // Empty State
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(start = 32.dp, end = 32.dp, bottom = bottomInset + 80.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.ic_brand_sparkle),
                                    contentDescription = "Sparkle",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(bottom = 12.dp)
                                )

                                Text(
                                    text = "No saved prayers yet",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF2C2420)
                                    ),
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = "Tap the heart at the end of a session to keep the words that moved you.",
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        color = Color(0xFF8B7E72),
                                        lineHeight = 22.sp
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        // LazyColumn of cards
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                top = 8.dp,
                                bottom = bottomInset + 100.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(savedPrayers, key = { it.id }) { prayer ->
                                val formattedDate = remember(prayer.date) {
                                    SimpleDateFormat("MMMM d, yyyy", Locale.US).format(Date(prayer.date))
                                }

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPrayer = prayer },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFDFCF8)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE8E0D4)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(20.dp)
                                    ) {
                                        // Top-left: Date formatted in 12sp taupe
                                        Text(
                                            text = formattedDate,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color(0xFF8B7E72)
                                        )

                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Middle: Memory verse in italic serif, warm black
                                        if (prayer.memoryVerse.isNotBlank()) {
                                            Text(
                                                text = "“${prayer.memoryVerse}”",
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontFamily = FontFamily.Serif,
                                                    fontStyle = FontStyle.Italic,
                                                    fontWeight = FontWeight.Normal,
                                                    color = Color(0xFF2C2420),
                                                    lineHeight = 22.sp
                                                ),
                                                maxLines = 3,
                                                overflow = TextOverflow.Ellipsis
                                            )

                                            Spacer(modifier = Modifier.height(10.dp))
                                        }

                                        // Bottom: 2-line snippet of transcript in sans-serif taupe
                                        if (prayer.transcript.isNotBlank()) {
                                            Text(
                                                text = prayer.transcript,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.SansSerif,
                                                    color = Color(0xFF8B7E72),
                                                    lineHeight = 18.sp
                                                ),
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Floating Bottom Navigation Pill
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = bottomInset + 16.dp)
                ) {
                    BottomNavPill(
                        activeTab = "favorites",
                        onTabSelected = { tab ->
                            when (tab) {
                                "home" -> onOpenHome()
                                "profile" -> onOpenProfile()
                            }
                        }
                    )
                }
            }
        }
    }
}
