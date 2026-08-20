package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.core.content.ContextCompat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import com.example.diagnostic.StreamState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.LogEntry
import com.example.model.LogLevel
import com.example.model.PipelineStatusState
import com.example.model.StatusState
import com.example.viewmodel.DiagnosticViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiagnosticScreen(
    viewModel: DiagnosticViewModel,
    onBack: (() -> Unit)? = null
) {
    if (onBack != null) {
        androidx.activity.compose.BackHandler {
            onBack()
        }
    }
    val context = LocalContext.current

    val pipelineStatus by viewModel.pipelineStatus.collectAsState()
    val isConnecting by viewModel.isConnecting.collectAsState()
    val streamState by viewModel.streamState.collectAsState()
    val connectionErrorMessage by viewModel.connectionErrorMessage.collectAsState()
    val logEntries by viewModel.logEntries.collectAsState()

    val apiKeyInput by viewModel.apiKeyInput.collectAsState()
    val modelNameInput by viewModel.modelNameInput.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()
    val debugMode by viewModel.debugMode.collectAsState()
    val isMicMuted by viewModel.isMicMuted.collectAsState()
    val isGeminiSpeaking by viewModel.isGeminiSpeaking.collectAsState()
    val isAudioPlaying by viewModel.isAudioPlaying.collectAsState()
    val isMicSending by viewModel.isMicSending.collectAsState()
    val wakeState by viewModel.wakeState.collectAsState()

    val isLockMode by viewModel.isLockMode.collectAsState()
    val lockModeState by viewModel.lockModeState.collectAsState()
    val isWaitingForResponse by viewModel.isWaitingForResponse.collectAsState()

    var isOnboardingComplete by remember {
        mutableStateOf(com.example.wake.WakePrefsManager.isOnboardingComplete(context))
    }

    if (!isOnboardingComplete) {
        OnboardingScreen(
            onOnboardingComplete = {
                isOnboardingComplete = true
            }
        )
        return
    }

    LaunchedEffect(Unit) {
        com.example.wake.WakePrefsManager.updateWakeState(context)
    }

    var showApiKey by remember { mutableStateOf(false) }
    var configExpanded by remember { mutableStateOf(true) }
    var selectedFontScale by remember { mutableStateOf("Default") } // Options: "Small", "Default", "Large", "Huge"

    val currentDensity = LocalDensity.current
    val fontScaleFactor = when (selectedFontScale) {
        "Small" -> 0.82f
        "Large" -> 1.25f
        "Huge" -> 1.45f
        else -> 1.0f
    }

    // Microphone runtime permission launcher for Full Duplex live voice
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.connect(context)
        } else {
            Toast.makeText(context, "Microphone permission required for Full Duplex live voice stream", Toast.LENGTH_SHORT).show()
            viewModel.connect(context)
        }
    }

    // SAF Document Creator launcher for manual file export selection
    val safExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null) {
            val (success, message) = viewModel.writeLogToUri(context, uri)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Lifecycle observer to cleanly release microphone and disconnect WebSocket when leaving app
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.disconnect()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.disconnect()
        }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = currentDensity.density,
            fontScale = currentDensity.fontScale * fontScaleFactor
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = "Gemini Live Diagnostic",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Engineering Pipeline Verification Tool",
                                fontSize = 12.sp,
                                color = Color.LightGray
                            )
                        }
                    },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color(0xFF0F172A),
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = Color(0xFF020617)
        ) { innerPadding ->
            val mainScrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(10.dp)
                    .verticalScroll(mainScrollState)
            ) {
                // Connection Unstable - Reconnecting Banner (Visible in all modes)
                if (streamState == com.example.diagnostic.StreamState.RECONNECTING) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(10.dp))
                            .testTag("reconnecting_banner_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFFFDE047),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Connection unstable - reconnecting...",
                                    color = Color(0xFFFEF08A),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "Session interrupted. Retrying connection...",
                                    color = Color(0xFFFDE68A),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Response Watchdog Banner: Waiting for Gemini...
                if (isWaitingForResponse) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .border(1.5.dp, Color(0xFF38BDF8), RoundedCornerShape(10.dp))
                            .testTag("waiting_for_gemini_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = Color(0xFF38BDF8),
                                strokeWidth = 2.5.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Waiting for Gemini...",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "User speech delivered, awaiting response from model...",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
                // Lock Mode Status Banner
                if (isLockMode) {
                    when (lockModeState.status) {
                        com.example.viewmodel.LockStatus.RECONNECTING -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .border(1.5.dp, Color(0xFFF59E0B), RoundedCornerShape(10.dp))
                                    .testTag("lock_mode_reconnecting_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = Color(0xFFFDE047),
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Reconnecting... please wait.",
                                            color = Color(0xFFFEF08A),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Text(
                                            text = "Session interrupted. Retrying connection while locked...",
                                            color = Color(0xFFFDE68A),
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B4B)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .border(1.5.dp, Color(0xFF6366F1), RoundedCornerShape(10.dp))
                                    .testTag("lock_mode_active_card")
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(22.dp),
                                            color = Color(0xFF818CF8),
                                            strokeWidth = 2.5.dp
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "Lock Mode Active — Morning Prayer",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Phone controls and back navigation locked until prayer completes.",
                                                color = Color(0xFFC7D2FE),
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    val isStalled by viewModel.isAutoStartStalled.collectAsState()
                                    if (isStalled) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Button(
                                            onClick = {
                                                val hasPermission = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED

                                                if (!hasPermission) {
                                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                } else {
                                                    viewModel.connect(context)
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1)),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .testTag("lock_mode_start_prayer_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.PlayArrow,
                                                contentDescription = "Start Prayer",
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Start Prayer", color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    when (lockModeState.status) {
                        com.example.viewmodel.LockStatus.COMPLETED -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF064E3B)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .border(1.5.dp, Color(0xFF10B981), RoundedCornerShape(10.dp))
                                    .testTag("lock_mode_completed_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Amen. You may continue your day.",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Your morning prayer session is complete.",
                                        color = Color(0xFFA7F3D0),
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.dismissLockState() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Return to app", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        com.example.viewmodel.LockStatus.ERROR -> {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .border(1.5.dp, Color(0xFFEF4444), RoundedCornerShape(10.dp))
                                    .testTag("lock_mode_error_card")
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Connection lost. Your phone is unlocked.",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = lockModeState.errorMessage ?: "Connection error occurred.",
                                        color = Color(0xFFFCA5A5),
                                        fontSize = 12.sp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(
                                        onClick = { viewModel.dismissLockState() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Text("Return to app", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        else -> {}
                    }
                }

                // Configuration Section (hidden in Lock Mode)
                if (!isLockMode) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { configExpanded = !configExpanded },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Diagnostic Parameters",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF38BDF8),
                                    fontSize = 14.sp
                                )
                                Icon(
                                    imageVector = if (configExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle Parameters",
                                    tint = Color.White
                                )
                            }

                            AnimatedVisibility(visible = configExpanded) {
                                Column(modifier = Modifier.padding(top = 8.dp)) {
                                    OutlinedTextField(
                                        value = apiKeyInput,
                                        onValueChange = { viewModel.apiKeyInput.value = it },
                                        label = { Text("Gemini API Key", color = Color.Gray, fontSize = 12.sp) },
                                        singleLine = true,
                                        visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                        trailingIcon = {
                                            IconButton(onClick = { showApiKey = !showApiKey }) {
                                                Icon(
                                                    imageVector = if (showApiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                    contentDescription = "Toggle API Key visibility",
                                                    tint = Color.Gray
                                                )
                                            }
                                        },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF38BDF8),
                                            unfocusedBorderColor = Color(0xFF475569),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("api_key_input")
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    OutlinedTextField(
                                        value = modelNameInput,
                                        onValueChange = { viewModel.modelNameInput.value = it },
                                        label = { Text("Live Model Name", color = Color.Gray, fontSize = 12.sp) },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = Color(0xFF38BDF8),
                                            unfocusedBorderColor = Color(0xFF475569),
                                            focusedTextColor = Color.White,
                                            unfocusedTextColor = Color.LightGray
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("model_name_input")
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Text(
                                        text = "UI Font Size Scale",
                                        color = Color(0xFF38BDF8),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("Small", "Default", "Large", "Huge").forEach { option ->
                                            FilterChip(
                                                selected = selectedFontScale == option,
                                                onClick = { selectedFontScale = option },
                                                label = { Text(option, fontSize = 11.sp) },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = Color(0xFF0284C7),
                                                    selectedLabelColor = Color.White,
                                                    containerColor = Color(0xFF334155),
                                                    labelColor = Color.LightGray
                                                )
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Checkbox(
                                            checked = debugMode,
                                            onCheckedChange = { viewModel.debugMode.value = it }
                                        )
                                        Text("Debug Verbose Mode", color = Color.LightGray, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

            // Responsive Status Indicator Grid
            StatusIndicatorCard(statusState = pipelineStatus)

            Spacer(modifier = Modifier.height(8.dp))

            // Wake Detector Readout Card
            WakeDetectorCard(wakeState = wakeState)

            if (!isLockMode) {
                // Developer Wake Test Panel
                DeveloperWakeTestPanel(wakeState = wakeState, context = context)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Conversation Turn & Mic Status Banner
            val isDisconnected = streamState == StreamState.IDLE || streamState == StreamState.DISCONNECTED
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDisconnected) Color(0xFF1E293B) else if (isMicSending) Color(0xFF064E3B) else Color(0xFF312E81)
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .border(
                        1.dp,
                        if (isDisconnected) Color(0xFF475569) else if (isMicSending) Color(0xFF34D399) else Color(0xFF818CF8),
                        RoundedCornerShape(8.dp)
                    )
                    .testTag("turn_status_banner")
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isDisconnected) Icons.Default.MicOff else if (isMicSending) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = null,
                        tint = if (isDisconnected) Color(0xFF94A3B8) else if (isMicSending) Color(0xFF6EE7B7) else Color(0xFFA5B4FC),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when {
                                isDisconnected -> "Not connected - press Connect to start"
                                isGeminiSpeaking -> "Gemini is speaking... Mic muted"
                                isAudioPlaying -> "Gemini finishing..."
                                else -> "Your turn - speak now"
                            },
                            color = if (isDisconnected) Color(0xFFCBD5E1) else if (isMicSending) Color(0xFFD1FAE5) else Color(0xFFE0E7FF),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = when {
                                isDisconnected -> "Microphone is offline"
                                isGeminiSpeaking -> "No Interruption Mode active"
                                isAudioPlaying -> "Draining playback audio buffer"
                                else -> "Microphone listening for user speech"
                            },
                            color = if (isDisconnected) Color(0xFF94A3B8) else if (isMicSending) Color(0xFFA7F3D0) else Color(0xFFC7D2FE),
                            fontSize = 11.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reconnecting non-blocking indicator banner
            AnimatedVisibility(visible = streamState == StreamState.RECONNECTING) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF451A03)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                        .testTag("reconnecting_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color(0xFFFDE047),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Connection unstable – reconnecting…",
                            color = Color(0xFFFEF08A),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Connection failed banner after reconnection attempts exhausted
            AnimatedVisibility(visible = !isConnecting && connectionErrorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF450A0A)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .testTag("reconnect_error_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .padding(12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = connectionErrorMessage ?: "Connection failed after multiple attempts.",
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (!hasPermission) {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    viewModel.connect(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("banner_reconnect_button")
                        ) {
                            Text("Reconnect", fontSize = 12.sp)
                        }
                    }
                }
            }

            if (!isLockMode) {
                // Responsive Action Buttons Wrapping FlowRow
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED

                            if (!hasPermission) {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.connect(context)
                            }
                        },
                        enabled = !isConnecting && com.example.wake.WakeDetectorService.overlayPage.value != com.example.wake.OverlayPage.SESSION,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("connect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Connect",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (connectionErrorMessage != null) "Reconnect" else "Connect", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.disconnect() },
                        enabled = isConnecting && com.example.wake.WakeDetectorService.overlayPage.value != com.example.wake.OverlayPage.SESSION,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("disconnect_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Disconnect",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Disconnect", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.toggleMicMute() },
                        enabled = isConnecting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isMicMuted) Color(0xFFBE123C) else Color(0xFF0D9488)
                        ),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("mute_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (isMicMuted) "Unmute Mic" else "Mute Mic",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isMicMuted) "Mic Muted" else "Mic Live", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { viewModel.clearLog() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("clear_log_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear Log",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Clear Log", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val copied = viewModel.copyLogToClipboard(context)
                            if (copied) {
                                Toast.makeText(context, "Log copied to clipboard", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Copy log failed", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("copy_log_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Log",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy Log", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val (success, message) = viewModel.exportLogToDownloads(context)
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            if (!success) {
                                safExportLauncher.launch(viewModel.generateExportFilename())
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.testTag("export_log_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Export Log",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export Log (.txt)", fontSize = 12.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Log Window Header & AutoScroll Checkbox
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Diagnostic Terminal Log",
                    fontWeight = FontWeight.Bold,
                    color = Color.LightGray,
                    fontSize = 13.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoScroll,
                        onCheckedChange = { viewModel.autoScroll.value = it },
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto-Scroll", color = Color.Gray, fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Scrollable Log Window
            LogWindow(
                logEntries = logEntries,
                autoScroll = autoScroll,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }
    }
}
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StatusIndicatorCard(statusState: PipelineStatusState) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF1E293B), RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Pipeline Steps",
                        tint = Color(0xFF94A3B8)
                    )
                    Text(
                        text = "Pipeline Status Steps",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(6.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        StatusItem("SDK", statusState.sdkStatus)
                        StatusItem("API Key", statusState.apiKeyStatus)
                        StatusItem("Live Client", statusState.liveClientStatus)
                        StatusItem("WebSocket", statusState.webSocketStatus)
                        StatusItem("Session", statusState.sessionStatus)
                        StatusItem("Ready", statusState.readyStatus)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Legend: ⚪ Not Started   🟡 In Progress   🟢 Success   🔴 Failed",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun StatusItem(label: String, status: StatusState) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Text(
            text = status.symbol,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = when (status) {
                StatusState.NOT_STARTED -> Color(0xFF94A3B8)
                StatusState.IN_PROGRESS -> Color(0xFFFACC15)
                StatusState.SUCCESS -> Color(0xFF4ADE80)
                StatusState.FAILED -> Color(0xFFF87171)
            }
        )
    }
}

@Composable
fun LogWindow(
    logEntries: List<LogEntry>,
    autoScroll: Boolean,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logEntries.size, autoScroll) {
        if (autoScroll && logEntries.isNotEmpty()) {
            listState.scrollToItem(logEntries.size - 1)
        }
    }

    Surface(
        color = Color(0xFF030712),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.border(1.dp, Color(0xFF1E293B), RoundedCornerShape(6.dp))
    ) {
        SelectionContainer {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
            ) {
                items(logEntries) { entry ->
                    LogEntryRow(entry = entry)
                }
            }
        }
    }
}

@Composable
fun LogEntryRow(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }

    val levelColor = when (entry.level) {
        LogLevel.INFO -> Color(0xFF38BDF8)
        LogLevel.SUCCESS -> Color(0xFF4ADE80)
        LogLevel.ERROR -> Color(0xFFF87171)
        LogLevel.WARN -> Color(0xFFFACC15)
        LogLevel.DEBUG -> Color(0xFF94A3B8)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = !entry.details.isNullOrBlank()) {
                expanded = !expanded
            }
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "[${entry.timestamp}] ",
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )
            Text(
                text = "${entry.level.label} ",
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = levelColor
            )
            Text(
                text = entry.message,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = Color(0xFFE2E8F0),
                modifier = Modifier.weight(1f)
            )
        }

        if (!entry.details.isNullOrBlank()) {
            Text(
                text = if (expanded) "▲ Hide exception details" else "▼ Show stack trace & details",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = Color(0xFFF87171),
                modifier = Modifier.padding(start = 16.dp, top = 2.dp)
            )

            AnimatedVisibility(visible = expanded) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                        .background(Color(0xFF1E1010), RoundedCornerShape(4.dp))
                        .padding(6.dp)
                ) {
                    Text(
                        text = entry.details ?: "",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFFFECACA)
                    )
                }
            }
        }
    }
}

@Composable
fun WakeDetectorCard(wakeState: com.example.wake.WakeState) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
            .testTag("wake_detector_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Wake Detector Readout",
                        tint = Color(0xFFF59E0B)
                    )
                    Text(
                        text = "Wake Detector Readout",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFF59E0B),
                        fontSize = 13.sp
                    )
                }
                Surface(
                    color = if (wakeState.wouldTriggerNextUnlock) Color(0xFF065F46) else Color(0xFF334155),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (wakeState.wouldTriggerNextUnlock) "WILL TRIGGER" else "NO TRIGGER",
                        color = if (wakeState.wouldTriggerNextUnlock) Color(0xFF34D399) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    val sdf = remember { java.text.SimpleDateFormat("HH:mm:ss (MM/dd)", java.util.Locale.US) }
                    val now = System.currentTimeMillis()

                    val lastScreenOffText = if (wakeState.lastScreenOff > 0) {
                        "${sdf.format(java.util.Date(wakeState.lastScreenOff))} (${String.format(java.util.Locale.US, "%.1f", wakeState.offHours)}h ago)"
                    } else {
                        "No record"
                    }

                    val lastPrayerText = if (wakeState.lastPrayerCompleted > 0) {
                        "${sdf.format(java.util.Date(wakeState.lastPrayerCompleted))} (${String.format(java.util.Locale.US, "%.1f", wakeState.sincePrayerHours)}h ago)"
                    } else {
                        "Never / No record"
                    }

                    val snoozeText = if (wakeState.snoozeUntil > now) {
                        val snoozeMin = ((wakeState.snoozeUntil - now) / 60000L).coerceAtLeast(1)
                        "${sdf.format(java.util.Date(wakeState.snoozeUntil))} (in ${snoozeMin}m)"
                    } else {
                        "Inactive"
                    }

                    Text("• Last Screen OFF: $lastScreenOffText", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    Text("• Last Prayer Completed: $lastPrayerText", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    Text("• Snooze Until: $snoozeText", color = Color(0xFFCBD5E1), fontSize = 11.sp)
                    val modeLabel = if (wakeState.useTestThresholds) "TEST (1m off / 5m cooldown)" else "REAL (4h off / 12h cooldown)"
                    Text("• Threshold Mode: $modeLabel", color = if (wakeState.useTestThresholds) Color(0xFFFACC15) else Color(0xFFCBD5E1), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    if (wakeState.overrideNextUnlock) {
                        Text("• Test Override: PENDING (Next screen unlock will force trigger)", color = Color(0xFFF43F5E), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "• Next Unlock Result: ${if (wakeState.wouldTriggerNextUnlock) "TRIGGER PRAYER ALERT" else "NO TRIGGER (Cooldown / Screen-off insufficient)"}",
                        color = if (wakeState.wouldTriggerNextUnlock) Color(0xFF6EE7B7) else Color(0xFF94A3B8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeveloperWakeTestPanel(
    wakeState: com.example.wake.WakeState,
    context: android.content.Context
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .border(1.dp, Color(0xFF0284C7), RoundedCornerShape(8.dp))
            .testTag("developer_wake_test_panel")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Developer Wake Test Panel",
                        tint = Color(0xFF38BDF8)
                    )
                    Text(
                        text = "Developer Wake Test",
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp
                    )
                    Surface(
                        color = Color(0xFF0284C7).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "DEV ONLY",
                            color = Color(0xFF38BDF8),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Current Mode: " + if (wakeState.useTestThresholds) "TEST (1 min off / 5 min cooldown)" else "REAL (4 hours off / 12 hours cooldown)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (wakeState.useTestThresholds) Color(0xFFFACC15) else Color(0xFF94A3B8)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // BUTTON 1 — "Simulate Wake Trigger"
                        Button(
                            onClick = {
                                com.example.wake.WakeDetectorService.simulateWakeTrigger(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("simulate_wake_trigger_button")
                        ) {
                            Text("Simulate Wake Trigger", fontSize = 11.sp)
                        }

                        // BUTTON 2 — "Inject Fake Sleep + Override Next Unlock"
                        Button(
                            onClick = {
                                com.example.wake.WakePrefsManager.injectFakeSleep(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("inject_fake_sleep_button")
                        ) {
                            Text("Inject Fake Sleep + Override Next Unlock", fontSize = 11.sp)
                        }

                        // Preview Missed-Day Simulation Toggle
                        val isPreviewMissedDay = com.example.wake.WakePrefsManager.getPreviewMissedDay(context)
                        FilterChip(
                            selected = isPreviewMissedDay,
                            onClick = {
                                com.example.wake.WakePrefsManager.setPreviewMissedDay(context, !isPreviewMissedDay)
                            },
                            label = {
                                Text(
                                    text = if (isPreviewMissedDay) "Preview Missed-Day: ON" else "Preview missed-day design (simulation)",
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFB4574E),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF334155),
                                labelColor = Color.LightGray
                            ),
                            modifier = Modifier.testTag("preview_missed_day_chip")
                        )

                        // Developer Notification Testing (False Notification Button in Profile)
                        val isDevNotifTest = wakeState.devNotificationTestEnabled
                        FilterChip(
                            selected = isDevNotifTest,
                            onClick = {
                                com.example.wake.WakePrefsManager.setDevNotificationTestEnabled(context, !isDevNotifTest)
                            },
                            label = {
                                Text(
                                    text = if (isDevNotifTest) "Profile Notification Test Button: ON" else "False Notification in Profile: OFF",
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF059669), // Emerald Green
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF334155),
                                labelColor = Color.LightGray
                            ),
                            modifier = Modifier.testTag("toggle_dev_notification_test_chip")
                        )

                        // Preview Pray Now Simulation Toggle
                        val isPreviewPrayNow = com.example.wake.WakePrefsManager.getPreviewPrayNow(context)
                        FilterChip(
                            selected = isPreviewPrayNow,
                            onClick = {
                                com.example.wake.WakePrefsManager.setPreviewPrayNow(context, !isPreviewPrayNow)
                            },
                            label = {
                                Text(
                                    text = if (isPreviewPrayNow) "Force 'Pray Now' Button: ON" else "Force 'Pray Now' button (simulation)",
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFB4574E),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF334155),
                                labelColor = Color.LightGray
                            ),
                            modifier = Modifier.testTag("preview_pray_now_chip")
                        )

                        // BUTTON 3 — "Test Thresholds (1 min off / 5 min cooldown)" toggle
                        FilterChip(
                            selected = wakeState.useTestThresholds,
                            onClick = {
                                com.example.wake.WakePrefsManager.setUseTestThresholds(context, !wakeState.useTestThresholds)
                            },
                            label = {
                                Text(
                                    text = if (wakeState.useTestThresholds) "Test Thresholds: ON (1m/5m)" else "Test Thresholds: OFF (4h/12h)",
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFD97706),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFF334155),
                                labelColor = Color.LightGray
                            ),
                            modifier = Modifier.testTag("toggle_test_thresholds_chip")
                        )

                        // BUTTON — Overlay Permission
                        val hasOverlayPermission = android.provider.Settings.canDrawOverlays(context)
                        FilterChip(
                            selected = hasOverlayPermission,
                            onClick = {
                                if (!hasOverlayPermission) {
                                    val intent = android.content.Intent(
                                        android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                        android.net.Uri.parse("package:${context.packageName}")
                                    )
                                    context.startActivity(intent)
                                }
                            },
                            label = {
                                Text(
                                    text = if (hasOverlayPermission) "Overlay Permission: GRANTED" else "Request Overlay Permission",
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF059669),
                                selectedLabelColor = Color.White,
                                containerColor = Color(0xFFDC2626),
                                labelColor = Color.White
                            ),
                            modifier = Modifier.testTag("overlay_permission_chip")
                        )

                        // BUTTON 4 — "Reset Wake Data"
                        Button(
                            onClick = {
                                com.example.wake.WakePrefsManager.resetWakeData(context)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.testTag("reset_wake_data_button")
                        ) {
                            Text("Reset Wake Data", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}
