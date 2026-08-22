package com.example.ui.key

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.api.ApiKeyProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val YOUTUBE_TUTORIAL_URL = ""
private const val AI_STUDIO_KEY_URL = "https://aistudio.google.com/app/apikey"

@Composable
fun KeySetupContent(
    onSuccess: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    isModalOrSheet: Boolean = false
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()

    var keyInput by rememberSaveable { mutableStateOf("") }
    var launchTimestamp by rememberSaveable { mutableLongStateOf(0L) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var secondsElapsed by remember { mutableIntStateOf(0) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val scrollState = rememberScrollState()

    // 9-second timer effect when user leaves to get key
    LaunchedEffect(launchTimestamp) {
        if (launchTimestamp > 0L) {
            while (true) {
                val now = System.currentTimeMillis()
                val diff = ((now - launchTimestamp) / 1000).toInt()
                secondsElapsed = diff
                if (diff >= 9) break
                delay(500)
            }
        }
    }

    val isTimerActive = launchTimestamp > 0L && secondsElapsed < 9
    val isTimerReady = launchTimestamp > 0L && secondsElapsed >= 9

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isModalOrSheet) Modifier.verticalScroll(scrollState) else Modifier)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Icon Badge
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(Color(0xFFE8E0D4), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Key,
                contentDescription = null,
                tint = Color(0xFFB4574E),
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Title - Compact 1 line
        Text(
            text = "Next up — your free key",
            style = TextStyle(
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                color = Color(0xFF2C2420)
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Body Description - Compact max 2 lines
        Text(
            text = "First Light is 100% free. It uses your own free Google AI key so your prayers remain private and no subscription is ever needed. Follow the 3 quick steps below.",
            style = TextStyle(
                fontFamily = FontFamily.SansSerif,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF8B7E72),
                lineHeight = 17.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )

        Spacer(modifier = Modifier.height(10.dp))

        // ─── 3-STEP WALKTHROUGH PAGER ───
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFFDFCF8))
                .border(1.dp, Color(0xFFE8E0D4), RoundedCornerShape(16.dp))
                .padding(vertical = 12.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(470.dp)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (page) {
                        // ─── STEP 1: TERMS OF SERVICE IMAGE ───
                        0 -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                GuideImageCard(
                                    type = GuideImageType.TOS,
                                    contentDescription = "Accept Terms of Service guide",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "1. Tick the Terms box, then tap Continue. (Emails box is optional).",
                                fontSize = 13.sp,
                                color = Color(0xFF2C2420),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Step 1 Navigation: Next
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                                    .testTag("guide_step1_next_button"),
                                shape = RoundedCornerShape(23.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFB4574E),
                                    contentColor = Color(0xFFFDFCF8)
                                )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = "Next",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // ─── STEP 2: COPY API KEY IMAGE + LAUNCH BROWSER ───
                        1 -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                GuideImageCard(
                                    type = GuideImageType.KEYS,
                                    contentDescription = "Copy API key guide",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "2. Tap the copy icon next to your key. If none exists, tap Create API key first.",
                                fontSize = 13.sp,
                                color = Color(0xFF2C2420),
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(horizontal = 6.dp)
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            // Step 2 Navigation: Back + Open Google
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(0)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.38f)
                                        .height(46.dp)
                                        .testTag("guide_step2_back_button"),
                                    shape = RoundedCornerShape(23.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE8E0D4)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF8B7E72)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Back", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                Button(
                                    onClick = {
                                        launchTimestamp = System.currentTimeMillis()
                                        secondsElapsed = 0
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_KEY_URL))
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
                                        }
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(2)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.62f)
                                        .height(46.dp)
                                        .testTag("guide_step2_open_button"),
                                    shape = RoundedCornerShape(23.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFB4574E),
                                        contentColor = Color(0xFFFDFCF8)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Open Google",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.ArrowOutward,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // ─── STEP 3: LIVE ACTIVE PASTE CARD ───
                        2 -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    shape = RoundedCornerShape(16.dp),
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = if (keyInput.isNotBlank()) Color(0xFF6B8F5A) else Color(0xFFB4574E)
                                    ),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F4EC)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .animateContentSize()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        // Status / Badge
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(24.dp)
                                                    .background(
                                                        if (keyInput.isNotBlank()) Color(0xFF6B8F5A) else Color(0xFFB4574E),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = if (keyInput.isNotBlank()) "✓" else "3",
                                                    color = Color(0xFFFDFCF8),
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = when {
                                                    keyInput.isNotBlank() -> "Key entered — ready to connect"
                                                    isTimerActive -> "Opening Google AI Studio... (${9 - secondsElapsed}s)"
                                                    else -> "Paste your copied key here"
                                                },
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = when {
                                                    keyInput.isNotBlank() -> Color(0xFF6B8F5A)
                                                    isTimerActive -> Color(0xFFB4574E)
                                                    else -> Color(0xFF2C2420)
                                                }
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(12.dp))

                                        // Real Interactive Paste Input Field
                                        OutlinedTextField(
                                            value = keyInput,
                                            onValueChange = {
                                                keyInput = it
                                                errorMessage = null
                                            },
                                            placeholder = {
                                                Text(
                                                    text = "AIzaSy...",
                                                    fontSize = 13.sp,
                                                    color = Color(0xFF8B7E72),
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Key,
                                                    contentDescription = null,
                                                    tint = if (keyInput.isNotBlank()) Color(0xFF6B8F5A) else Color(0xFF8B7E72),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(end = 4.dp)
                                                ) {
                                                    if (keyInput.isNotBlank()) {
                                                        IconButton(
                                                            onClick = {
                                                                keyInput = ""
                                                                errorMessage = null
                                                            },
                                                            modifier = Modifier.size(28.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Clear,
                                                                contentDescription = "Clear",
                                                                tint = Color(0xFF8B7E72),
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }

                                                    TextButton(
                                                        onClick = {
                                                            try {
                                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                                val clip = clipboard?.primaryClip
                                                                if (clip != null && clip.itemCount > 0) {
                                                                    val pasted = clip.getItemAt(0).coerceToText(context).toString().trim()
                                                                    if (pasted.isNotBlank()) {
                                                                        keyInput = pasted
                                                                        errorMessage = null
                                                                    }
                                                                }
                                                            } catch (e: Exception) {
                                                                Log.e("KeySetup", "Clipboard error: ${e.message}")
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(6.dp),
                                                        modifier = Modifier.height(32.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.ContentPaste,
                                                            contentDescription = "Paste",
                                                            tint = Color(0xFFB4574E),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = "Paste",
                                                            color = Color(0xFFB4574E),
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 13.sp
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Password,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = { focusManager.clearFocus() }
                                            ),
                                            shape = RoundedCornerShape(10.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedTextColor = Color(0xFF2C2420),
                                                unfocusedTextColor = Color(0xFF2C2420),
                                                focusedContainerColor = Color(0xFFFDFCF8),
                                                unfocusedContainerColor = Color(0xFFFDFCF8),
                                                cursorColor = Color(0xFFB4574E),
                                                focusedBorderColor = Color(0xFFB4574E),
                                                unfocusedBorderColor = Color(0xFFE8E0D4)
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("api_key_input_field")
                                        )

                                        if (launchTimestamp == 0L) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Row(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .clickable {
                                                        launchTimestamp = System.currentTimeMillis()
                                                        secondsElapsed = 0
                                                        try {
                                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AI_STUDIO_KEY_URL))
                                                            context.startActivity(intent)
                                                        } catch (_: Exception) {}
                                                    }
                                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "Need your key? Open Google AI Studio",
                                                    fontSize = 11.sp,
                                                    color = Color(0xFFB4574E),
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ArrowOutward,
                                                    contentDescription = null,
                                                    tint = Color(0xFFB4574E),
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Error message (friendly retry text)
                            AnimatedVisibility(
                                visible = errorMessage != null,
                                enter = fadeIn() + expandVertically(),
                                exit = fadeOut() + shrinkVertically()
                            ) {
                                errorMessage?.let { msg ->
                                    Text(
                                        text = msg,
                                        color = Color(0xFFB4574E),
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp, start = 6.dp, end = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            // Step 3 Navigation: Back + Connect & Finish
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(1)
                                        }
                                    },
                                    modifier = Modifier
                                        .weight(0.35f)
                                        .height(46.dp)
                                        .testTag("guide_step3_back_button"),
                                    shape = RoundedCornerShape(23.dp),
                                    border = BorderStroke(1.dp, Color(0xFFE8E0D4)),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFF8B7E72)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Back", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                }

                                Button(
                                    onClick = {
                                        focusManager.clearFocus()
                                        val trimmed = keyInput.trim()
                                        if (trimmed.isEmpty()) {
                                            errorMessage = "Please paste your key above, then tap connect."
                                            return@Button
                                        }
                                        if (!ApiKeyProvider.isKeyValidFormat(trimmed)) {
                                            errorMessage = "We couldn't connect with this key. Please check that you copied the entire key and try again."
                                            return@Button
                                        }

                                        isLoading = true
                                        errorMessage = null

                                        coroutineScope.launch {
                                            val result = ApiKeyProvider.validateKeyLive(trimmed)
                                            isLoading = false
                                            if (result.isSuccess) {
                                                ApiKeyProvider.setUserApiKey(context, trimmed)
                                                onSuccess()
                                            } else {
                                                errorMessage = "We couldn't connect with this key. Please check that you copied the entire key and try again."
                                            }
                                        }
                                    },
                                    enabled = !isLoading && keyInput.trim().isNotBlank(),
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .height(46.dp)
                                        .shadow(
                                            elevation = if (keyInput.isNotBlank()) 2.dp else 0.dp,
                                            shape = RoundedCornerShape(23.dp),
                                            ambientColor = Color(0x26000000),
                                            spotColor = Color(0x26000000)
                                        )
                                        .testTag("connect_finish_button"),
                                    shape = RoundedCornerShape(23.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF2C2420),
                                        contentColor = Color(0xFFFDFCF8),
                                        disabledContainerColor = Color(0xFFE8E0D4),
                                        disabledContentColor = Color(0xFF8B7E72)
                                    )
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            color = Color(0xFFFDFCF8),
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Connecting...",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    } else {
                                        Text(
                                            text = "Connect & Finish",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Step Dot Indicators
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(3) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0xFFB4574E) else Color(0xFFE8E0D4)
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Video tutorial link (if URL is set)
        if (YOUTUBE_TUTORIAL_URL.isNotBlank()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(YOUTUBE_TUTORIAL_URL))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.PlayCircleOutline,
                    contentDescription = null,
                    tint = Color(0xFF8B7E72),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Watch a 2-minute video",
                    fontSize = 12.sp,
                    color = Color(0xFF8B7E72),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // "I'll do this later" Secondary Skip Link
        TextButton(
            onClick = {
                focusManager.clearFocus()
                onSkip()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .testTag("skip_key_setup_button")
        ) {
            Text(
                text = "I'll do this later",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF8B7E72)
            )
        }
    }
}
