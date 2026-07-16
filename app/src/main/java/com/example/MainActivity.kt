package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme(darkTheme = true) { // Always darkTheme for that premium terminal vibe
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = Color(0xFF0F172A) // Sleek midnight background
                ) { innerPadding ->
                    MainScreen(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    )
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // ViewModel state
    val scanState by viewModel.scanState.collectAsState()
    val currentStepIndex by viewModel.currentStepIndex.collectAsState()
    val currentStepState by viewModel.currentStepState.collectAsState()
    val completedScanCount by viewModel.completedScanCount.collectAsState()
    val isSimulationMode by viewModel.isSimulationMode.collectAsState()
    val autoSimulateSuccess by viewModel.autoSimulateSuccess.collectAsState()
    val feedbackTimeoutSeconds by viewModel.feedbackTimeoutSeconds.collectAsState()
    val stepDelayMillis by viewModel.stepDelayMillis.collectAsState()
    val terminalLogs by viewModel.terminalLogs.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val selectedDevice by viewModel.selectedDevice.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val connectionError by viewModel.connectionError.collectAsState()

    val isBleMode by viewModel.isBleMode.collectAsState()
    val isBangleMode by viewModel.isBangleMode.collectAsState()
    val fluorescentOnDelay by viewModel.fluorescentOnDelay.collectAsState()
    val fluorescentOffDelay by viewModel.fluorescentOffDelay.collectAsState()
    val simulantOnDelay by viewModel.simulantOnDelay.collectAsState()
    val shortPhosphoOnDelay by viewModel.shortPhosphoOnDelay.collectAsState()
    val shortPhosphoOffDelay by viewModel.shortPhosphoOffDelay.collectAsState()

    var hasBTScanPermission by remember { mutableStateOf(false) }
    var hasBTConnectPermission by remember { mutableStateOf(false) }

    // Dynamic Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasBTScanPermission = permissions[Manifest.permission.BLUETOOTH_SCAN] ?: false
        hasBTConnectPermission = permissions[Manifest.permission.BLUETOOTH_CONNECT] ?: false
        
        if (hasBTConnectPermission) {
            viewModel.refreshPairedDevices()
        } else if (!isSimulationMode) {
            Toast.makeText(context, "Bluetooth permissions required for real hardware mode", Toast.LENGTH_LONG).show()
        }
    }

    // Check permissions and initialize
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scanGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            val connectGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            hasBTScanPermission = scanGranted
            hasBTConnectPermission = connectGranted
            
            if (!scanGranted || !connectGranted) {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.BLUETOOTH_SCAN,
                        Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            }
        } else {
            hasBTScanPermission = true
            hasBTConnectPermission = true
            viewModel.refreshPairedDevices()
        }
    }

    // Scroll container
    val mainScrollState = rememberScrollState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(mainScrollState)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header Banner
            HeaderBanner(
                isSimulationMode = isSimulationMode,
                connectionState = connectionState,
                deviceName = selectedDevice?.name ?: selectedDevice?.address
            )

            // 2. Hero Action Panel (Pulsing trigger and Counter)
            HeroActionPanel(
                scanState = scanState,
                completedScanCount = completedScanCount,
                currentStepIndex = currentStepIndex,
                currentStepState = currentStepState,
                steps = viewModel.steps,
                onStartClick = {
                    if (!isSimulationMode && !hasBTConnectPermission) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            permissionLauncher.launch(
                                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
                            )
                        }
                    } else {
                        viewModel.startScan()
                    }
                },
                onStopClick = { viewModel.stopScan() },
                onResetClick = { viewModel.resetCount() }
            )

            // 3. Sequential Timeline Board
            TimelineBoard(
                steps = viewModel.steps,
                currentStepIndex = currentStepIndex,
                currentStepState = currentStepState,
                scanState = scanState
            )

            // 4. Hardware Connection & Sim Panel
            ConfigAndConnectionPanel(
                isSimulationMode = isSimulationMode,
                autoSimulateSuccess = autoSimulateSuccess,
                pairedDevices = pairedDevices,
                selectedDevice = selectedDevice,
                connectionState = connectionState,
                connectionError = connectionError,
                feedbackTimeout = feedbackTimeoutSeconds,
                stepDelay = stepDelayMillis,
                onModeChange = { viewModel.setSimulationMode(it) },
                onAutoSimulateChange = { viewModel.setAutoSimulateSuccess(it) },
                onDeviceSelect = { viewModel.selectDevice(it) },
                onConnectClick = { viewModel.connectDevice() },
                onDisconnectClick = { viewModel.disconnectDevice() },
                onRefreshDevices = { viewModel.refreshPairedDevices() },
                onTimeoutChange = { viewModel.setFeedbackTimeout(it) },
                onStepDelayChange = { viewModel.setStepDelay(it) },
                isBluetoothEnabled = viewModel.isBluetoothEnabled(),
                isBleMode = isBleMode,
                onBleModeChange = { viewModel.setBleMode(it) },
                isBangleMode = isBangleMode,
                onBangleModeChange = { viewModel.setBangleMode(it) },
                fluorescentOnDelay = fluorescentOnDelay,
                onFluorescentOnDelayChange = { viewModel.setFluorescentOnDelay(it) },
                fluorescentOffDelay = fluorescentOffDelay,
                onFluorescentOffDelayChange = { viewModel.setFluorescentOffDelay(it) },
                simulantOnDelay = simulantOnDelay,
                onSimulantOnDelayChange = { viewModel.setSimulantOnDelay(it) },
                shortPhosphoOnDelay = shortPhosphoOnDelay,
                onShortPhosphoOnDelayChange = { viewModel.setShortPhosphoOnDelay(it) },
                shortPhosphoOffDelay = shortPhosphoOffDelay,
                onShortPhosphoOffDelayChange = { viewModel.setShortPhosphoOffDelay(it) }
            )

            // 5. Simulation Action Deck
            if (isSimulationMode) {
                SimulationActionDeck(
                    scanState = scanState,
                    currentStepState = currentStepState,
                    onSimulateSuccess = { viewModel.simulateFeedback() },
                    onSimulateTimeout = { /* Let the naturally active timer expire */ }
                )
            }

            // 6. Live Logging Console
            TerminalConsole(
                logs = terminalLogs,
                onClearLogs = { viewModel.clearLogs() }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 7. Feedback Timeout Dialog
        if (scanState == ScanState.ALERT_NO_FEEDBACK) {
            val stepName = viewModel.steps.getOrNull(currentStepIndex)?.name ?: "Step ${currentStepIndex + 1}"
            FeedbackTimeoutDialog(
                stepName = stepName,
                timeoutSeconds = feedbackTimeoutSeconds,
                onDismiss = { viewModel.dismissAlert() }
            )
        }
    }
}

// ==================== SUBCOMPONENTS ====================

@Composable
fun HeaderBanner(
    isSimulationMode: Boolean,
    connectionState: BTConnectionState,
    deviceName: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "BT SCAN COMMANDER",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    ),
                    color = Color.White
                )
                Text(
                    text = "Automated Sequential Command Loop",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray
                )
            }

            // Connection Status Pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isSimulationMode -> Color(0xFF0891B2).copy(alpha = 0.2f)
                            connectionState == BTConnectionState.CONNECTED -> Color(0xFF059669).copy(alpha = 0.2f)
                            connectionState == BTConnectionState.CONNECTING -> Color(0xFFD97706).copy(alpha = 0.2f)
                            else -> Color(0xFF475569).copy(alpha = 0.2f)
                        }
                    )
                    .border(
                        width = 1.dp,
                        color = when {
                            isSimulationMode -> Color(0xFF06B6D4)
                            connectionState == BTConnectionState.CONNECTED -> Color(0xFF10B981)
                            connectionState == BTConnectionState.CONNECTING -> Color(0xFFF59E0B)
                            else -> Color(0xFF64748B)
                        },
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = when {
                            isSimulationMode -> Icons.Default.BugReport
                            connectionState == BTConnectionState.CONNECTED -> Icons.Default.BluetoothConnected
                            connectionState == BTConnectionState.CONNECTING -> Icons.Default.Bluetooth
                            else -> Icons.Default.BluetoothDisabled
                        },
                        contentDescription = "Status Icon",
                        tint = when {
                            isSimulationMode -> Color(0xFF22D3EE)
                            connectionState == BTConnectionState.CONNECTED -> Color(0xFF34D399)
                            connectionState == BTConnectionState.CONNECTING -> Color(0xFFFBBF24)
                            else -> Color(0xFF94A3B8)
                        },
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = when {
                            isSimulationMode -> "SIMULATED"
                            connectionState == BTConnectionState.CONNECTED -> "CONNECTED: ${deviceName ?: "BT"}"
                            connectionState == BTConnectionState.CONNECTING -> "CONNECTING..."
                            connectionState == BTConnectionState.ERROR -> "CONN ERROR"
                            else -> "DISCONNECTED"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            isSimulationMode -> Color(0xFF22D3EE)
                            connectionState == BTConnectionState.CONNECTED -> Color(0xFF34D399)
                            connectionState == BTConnectionState.CONNECTING -> Color(0xFFFBBF24)
                            else -> Color(0xFF94A3B8)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun HeroActionPanel(
    scanState: ScanState,
    completedScanCount: Int,
    currentStepIndex: Int,
    currentStepState: StepState,
    steps: List<ScanStep>,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Loop Count Widget
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "COMPLETED SCANS",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    ),
                    color = Color.LightGray
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "$completedScanCount",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (completedScanCount > 0) Color(0xFF10B981) else Color.White
                    )
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    IconButton(
                        onClick = onResetClick,
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("reset_count_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Count",
                            tint = Color.LightGray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

            // Current Step Details
            if (scanState == ScanState.SCANNING) {
                val currentStep = steps.getOrNull(currentStepIndex)
                if (currentStep != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "CURRENT ACTIVE ACTION",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        Text(
                            text = "Step ${currentStepIndex + 1}/${steps.size}: ${currentStep.name}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF38BDF8)
                        )
                        Text(
                            text = when (currentStepState) {
                                StepState.SENDING_ON -> "Sending On: [${currentStep.onCommands.joinToString(", ")}]"
                                StepState.WAITING_ON_FEEDBACK -> "📡 Awaiting feedback for On..."
                                StepState.SENDING_OFF -> "Sending Off: [${currentStep.offCommand ?: ""}]"
                                StepState.WAITING_OFF_FEEDBACK -> "📡 Awaiting feedback for Off..."
                                StepState.SUCCESS -> "✔ Step succeeded!"
                                StepState.TIMEOUT -> "❌ Feedback timed out!"
                                else -> "Preparing..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = when (currentStepState) {
                                StepState.SUCCESS -> Color(0xFF34D399)
                                StepState.TIMEOUT -> Color(0xFFF87171)
                                StepState.WAITING_ON_FEEDBACK, StepState.WAITING_OFF_FEEDBACK -> Color(0xFFFBBF24)
                                else -> Color.White
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = if (scanState == ScanState.ALERT_NO_FEEDBACK) "SEQUENCE HALTED" else "SYSTEM IDLE",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (scanState == ScanState.ALERT_NO_FEEDBACK) Color(0xFFF87171) else Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Main Scanning Control Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                if (scanState != ScanState.SCANNING) {
                    // Big Green Pulse Start Scan Button
                    Button(
                        onClick = onStartClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(56.dp)
                            .testTag("start_scan_button"),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Start Icon",
                                tint = Color.White
                            )
                            Text(
                                text = "START SCAN LOOP",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                } else {
                    // Big Pulsing Stop Button
                    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 0.7f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(800),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "pulseAlpha"
                    )

                    Button(
                        onClick = onStopClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(56.dp)
                            .alpha(alpha)
                            .testTag("stop_scan_button"),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop Icon",
                                tint = Color.White
                            )
                            Text(
                                text = "STOP SEQUENCE LOOP",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TimelineBoard(
    steps: List<ScanStep>,
    currentStepIndex: Int,
    currentStepState: StepState,
    scanState: ScanState
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "SEQUENCE PIPELINE (${steps.size} STEPS)",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = Color.LightGray
            )

            steps.forEachIndexed { idx, step ->
                val isActive = scanState == ScanState.SCANNING && currentStepIndex == idx
                val isCompleted = scanState == ScanState.SCANNING && idx < currentStepIndex
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isActive) Color(0xFF0F172A).copy(alpha = 0.5f) else Color.Transparent
                        )
                        .border(
                            width = if (isActive) 1.dp else 0.dp,
                            color = if (isActive) Color(0xFF38BDF8) else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Number Badge
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isCompleted -> Color(0xFF059669)
                                    isActive -> Color(0xFF0284C7)
                                    else -> Color(0xFF475569)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Complete",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "${step.index + 1}",
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Step Info
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = step.name,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                            ),
                            color = if (isActive) Color.White else Color.LightGray
                        )
                        Text(
                            text = "Command: ON [${step.onCommands.joinToString(", ").ifEmpty { "None" }}] | OFF [${step.offCommand ?: "None"}]",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }

                    // Active Step State Indicator
                    if (isActive) {
                        Text(
                            text = when (currentStepState) {
                                StepState.SENDING_ON -> "TX ON"
                                StepState.WAITING_ON_FEEDBACK -> "AWAIT RX"
                                StepState.SENDING_OFF -> "TX OFF"
                                StepState.WAITING_OFF_FEEDBACK -> "AWAIT RX"
                                StepState.SUCCESS -> "OK"
                                StepState.TIMEOUT -> "TIMEOUT"
                                else -> "..."
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                fontFamily = FontFamily.Monospace
                            ),
                            color = when (currentStepState) {
                                StepState.SUCCESS -> Color(0xFF34D399)
                                StepState.TIMEOUT -> Color(0xFFF87171)
                                StepState.WAITING_ON_FEEDBACK, StepState.WAITING_OFF_FEEDBACK -> Color(0xFFFBBF24)
                                else -> Color(0xFF38BDF8)
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    } else if (isCompleted) {
                        Text(
                            text = "DONE",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF10B981)
                        )
                    } else {
                        Text(
                            text = "READY",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                    }
                }
                
                if (idx < steps.size - 1) {
                    // Vertical dot/line visualization
                    Box(
                        modifier = Modifier
                            .padding(start = 21.dp)
                            .width(2.dp)
                            .height(10.dp)
                            .background(Color(0xFF334155))
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigAndConnectionPanel(
    isSimulationMode: Boolean,
    autoSimulateSuccess: Boolean,
    pairedDevices: List<BluetoothDevice>,
    selectedDevice: BluetoothDevice?,
    connectionState: BTConnectionState,
    connectionError: String?,
    feedbackTimeout: Int,
    stepDelay: Long,
    onModeChange: (Boolean) -> Unit,
    onAutoSimulateChange: (Boolean) -> Unit,
    onDeviceSelect: (BluetoothDevice) -> Unit,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onRefreshDevices: () -> Unit,
    onTimeoutChange: (Int) -> Unit,
    onStepDelayChange: (Long) -> Unit,
    isBluetoothEnabled: Boolean,
    
    // NEW BLE / CUSTOM DELAYS
    isBleMode: Boolean,
    onBleModeChange: (Boolean) -> Unit,
    isBangleMode: Boolean,
    onBangleModeChange: (Boolean) -> Unit,
    fluorescentOnDelay: Int,
    onFluorescentOnDelayChange: (Int) -> Unit,
    fluorescentOffDelay: Int,
    onFluorescentOffDelayChange: (Int) -> Unit,
    simulantOnDelay: Int,
    onSimulantOnDelayChange: (Int) -> Unit,
    shortPhosphoOnDelay: Int,
    onShortPhosphoOnDelayChange: (Int) -> Unit,
    shortPhosphoOffDelay: Int,
    onShortPhosphoOffDelayChange: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Config Icon",
                    tint = Color.LightGray
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "HARDWARE & CONFLICT CONFIG",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            // 1. Simulation Mode Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0F172A).copy(alpha = 0.4f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Demo Simulation Mode",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                    Text(
                        text = "Simulate feedback without physical Bluetooth hardware (recommended for applet review)",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        lineHeight = 14.sp
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                Switch(
                    checked = isSimulationMode,
                    onCheckedChange = onModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF06B6D4),
                        checkedTrackColor = Color(0xFF06B6D4).copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.testTag("simulation_mode_toggle")
                )
            }

            // 2. Setup options depending on Mode Selection
            if (isSimulationMode) {
                // Auto Success Simulation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Simulate Success Feedback",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.LightGray
                        )
                        Text(
                            text = "Saves manually clicking 'Feed back' button. Automatically advances step in 800ms.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                    Switch(
                        checked = autoSimulateSuccess,
                        onCheckedChange = onAutoSimulateChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF10B981)
                        )
                    )
                }
            } else {
                // Real Bluetooth Connections Section
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Real Bluetooth Setup",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.LightGray
                    )

                    // Bluetooth Mode Selector
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A))
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (!isBleMode) Color(0xFF334155) else Color.Transparent)
                                .clickable { onBleModeChange(false) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Classic (SPP)", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isBleMode) Color(0xFF0284C7) else Color.Transparent)
                                .clickable { onBleModeChange(true) }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("BLE UART", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        }
                    }

                    if (!isBluetoothEnabled) {
                        Text(
                            text = "⚠️ Bluetooth is disabled on your device! Please turn on Bluetooth.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFBBF24)
                        )
                    }

                    // Dropdown for bonded/discovered devices
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { expanded = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("paired_devices_dropdown"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = selectedDevice?.let { "${it.name ?: "Unnamed"} [${it.address}]" }
                                        ?: if (pairedDevices.isEmpty()) "No Devices Found" else "Select Device",
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Expand",
                                    tint = Color.LightGray
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(Color(0xFF1E293B))
                        ) {
                            pairedDevices.forEach { dev ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "${dev.name ?: "Unnamed"} [${dev.address}]",
                                            color = Color.White
                                        )
                                    },
                                    onClick = {
                                        onDeviceSelect(dev)
                                        expanded = false
                                    }
                                )
                            }
                            if (pairedDevices.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No devices found. Press refresh to scan.", color = Color.Gray) },
                                    onClick = { expanded = false }
                                )
                            }
                        }
                    }

                    // Row of Action buttons (Connect, Disconnect, Scan/Refresh)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onConnectClick,
                            enabled = selectedDevice != null && connectionState != BTConnectionState.CONNECTED && connectionState != BTConnectionState.CONNECTING,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("connect_device_button"),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("CONNECT", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = onDisconnectClick,
                            enabled = connectionState == BTConnectionState.CONNECTED || connectionState == BTConnectionState.CONNECTING,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                            modifier = Modifier
                                .weight(1.0f)
                                .height(44.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("DISCONNECT")
                        }

                        IconButton(
                            onClick = onRefreshDevices,
                            modifier = Modifier
                                .size(44.dp)
                                .background(Color(0xFF334155), RoundedCornerShape(8.dp)),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh devices list", tint = Color.White)
                        }
                    }

                    // Display Error Messages
                    connectionError?.let { err ->
                        Text(
                            text = "❌ $err",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            // 3. Customizable Delay Presets (From CameraView)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Camera Capture & Sequence Delays",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.LightGray
                )

                // Bangle Mode Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F172A).copy(alpha = 0.4f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bangle Mode", color = Color.White, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                        Text("Adds 1.5s capture padding (2.0s instead of 0.5s)", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = isBangleMode,
                        onCheckedChange = onBangleModeChange,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF06B6D4))
                    )
                }

                // Fluorescent ON delay slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fluorescent ON Delay", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Text("${fluorescentOnDelay}s", color = Color(0xFF22D3EE), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Slider(
                        value = fluorescentOnDelay.toFloat(),
                        onValueChange = { onFluorescentOnDelayChange(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(activeTrackColor = Color(0xFF22D3EE), thumbColor = Color(0xFF22D3EE))
                    )
                }

                // Fluorescent OFF delay slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fluorescent OFF Delay", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Text("${fluorescentOffDelay}s", color = Color(0xFF22D3EE), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Slider(
                        value = fluorescentOffDelay.toFloat(),
                        onValueChange = { onFluorescentOffDelayChange(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(activeTrackColor = Color(0xFF22D3EE), thumbColor = Color(0xFF22D3EE))
                    )
                }

                // Simulant ON delay slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Simulant ON Delay", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Text("${simulantOnDelay}s", color = Color(0xFF22D3EE), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Slider(
                        value = simulantOnDelay.toFloat(),
                        onValueChange = { onSimulantOnDelayChange(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(activeTrackColor = Color(0xFF22D3EE), thumbColor = Color(0xFF22D3EE))
                    )
                }

                // Short Phospho ON delay slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Short Phospho ON Delay", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Text("${shortPhosphoOnDelay}s", color = Color(0xFF22D3EE), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Slider(
                        value = shortPhosphoOnDelay.toFloat(),
                        onValueChange = { onShortPhosphoOnDelayChange(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(activeTrackColor = Color(0xFF22D3EE), thumbColor = Color(0xFF22D3EE))
                    )
                }

                // Short Phospho OFF delay slider
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Short Phosphor OFF Delay (Long Decay)", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                        Text("${shortPhosphoOffDelay}s", color = Color(0xFF22D3EE), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                    }
                    Slider(
                        value = shortPhosphoOffDelay.toFloat(),
                        onValueChange = { onShortPhosphoOffDelayChange(it.toInt()) },
                        valueRange = 0f..10f,
                        steps = 9,
                        colors = SliderDefaults.colors(activeTrackColor = Color(0xFF22D3EE), thumbColor = Color(0xFF22D3EE))
                    )
                }
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))

            // 4. General Loop Configurations (Timeout & Delay sliders)
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Timeout Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Feedback Timeout Limit",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                        Text(
                            text = "${feedbackTimeout}s",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFFFBBF24)
                        )
                    }
                    Slider(
                        value = feedbackTimeout.toFloat(),
                        onValueChange = { onTimeoutChange(it.toInt()) },
                        valueRange = 1f..15f,
                        steps = 14,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFBBF24),
                            activeTrackColor = Color(0xFFFBBF24)
                        )
                    )
                }

                // Step Delay Slider
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Inter-Step Execution Wait",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.LightGray
                        )
                        Text(
                            text = "${stepDelay}ms",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color(0xFF38BDF8)
                        )
                    }
                    Slider(
                        value = stepDelay.toFloat(),
                        onValueChange = { onStepDelayChange(it.toLong()) },
                        valueRange = 100f..5000f,
                        steps = 49,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF38BDF8),
                            activeTrackColor = Color(0xFF38BDF8)
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SimulationActionDeck(
    scanState: ScanState,
    currentStepState: StepState,
    onSimulateSuccess: () -> Unit,
    onSimulateTimeout: () -> Unit
) {
    val isWaiting = scanState == ScanState.SCANNING && 
            (currentStepState == StepState.WAITING_ON_FEEDBACK || currentStepState == StepState.WAITING_OFF_FEEDBACK)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF06B6D4).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.BugReport, contentDescription = "Sim Deck", tint = Color(0xFF22D3EE))
                Text(
                    text = "SIMULATE HARDWARE CONFLICT",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFF22D3EE)
                )
            }

            Text(
                text = "Manually trigger events to verify sequence logic, alarm sound alerts, and visual metrics.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onSimulateSuccess,
                    enabled = isWaiting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("simulate_feedback_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("FEED BACK (OK)", fontWeight = FontWeight.Bold, color = Color.White)
                }

                Button(
                    onClick = onSimulateTimeout,
                    enabled = isWaiting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .testTag("simulate_failure_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("TRIGGER TIMEOUT", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun TerminalConsole(
    logs: List<String>,
    onClearLogs: () -> Unit
) {
    val lazyListState = rememberLazyListState()

    // Auto scroll terminal logs to the bottom
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            lazyListState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF020617)), // Extreme dark black
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "Terminal",
                        tint = Color(0xFF10B981),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "LIVE SEQUENCE LOGS",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color(0xFF10B981)
                    )
                }

                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier
                        .size(28.dp)
                        .testTag("clear_logs_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Logs",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            HorizontalDivider(color = Color(0xFF1E293B))

            // Text Terminal Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Color(0xFF020617))
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "Awaiting execution...\nPress 'START SCAN LOOP' above to begin sending automated Bluetooth commands.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        ),
                        color = Color.DarkGray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(logs) { log ->
                            val color = when {
                                log.contains("[ERROR]") || log.contains("[FAILURE]") -> Color(0xFFEF4444)
                                log.contains("[RX]") -> Color(0xFF34D399)
                                log.contains("[SIM-RX]") -> Color(0xFF22D3EE)
                                log.contains("[TX]") || log.contains("[SIM-TX]") -> Color(0xFFF59E0B)
                                log.contains("✔ SUCCESS") -> Color(0xFF10B981)
                                else -> Color.LightGray
                            }
                            Text(
                                text = log,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp
                                ),
                                color = color
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FeedbackTimeoutDialog(
    stepName: String,
    timeoutSeconds: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Alert Warning",
                    tint = Color(0xFFEF4444)
                )
                Text(
                    text = "FEEDBACK NOT RECEIVED!",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "The scan sequence was aborted because Bluetooth feedback was not received.",
                    color = Color.LightGray
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF334155).copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Failed Step: $stepName",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFF87171)
                        )
                        Text(
                            text = "Timeout Period: ${timeoutSeconds} seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
                Text(
                    text = "📢 Sound alarm has been triggered to alert operators.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = Color(0xFFFBBF24)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                modifier = Modifier.testTag("dismiss_alert_button"),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("DISMISS & SILENCE ALARM", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        containerColor = Color(0xFF1E293B)
    )
}
