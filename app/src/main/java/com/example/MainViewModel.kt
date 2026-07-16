package com.example

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.media.AudioManager
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ScanState {
    IDLE,
    SCANNING,
    ALERT_NO_FEEDBACK
}

enum class StepState {
    PENDING,
    SENDING_ON,
    WAITING_ON_FEEDBACK,
    SENDING_OFF,
    WAITING_OFF_FEEDBACK,
    SUCCESS,
    TIMEOUT
}

data class ScanStep(
    val index: Int,
    val name: String,
    val onCommands: List<String>,
    val offCommand: String?,
    val description: String
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val bluetoothCommander = BluetoothCommander(application)

    // UI state streams
    private val _scanState = MutableStateFlow(ScanState.IDLE)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _currentStepIndex = MutableStateFlow(0)
    val currentStepIndex: StateFlow<Int> = _currentStepIndex.asStateFlow()

    private val _currentStepState = MutableStateFlow(StepState.PENDING)
    val currentStepState: StateFlow<StepState> = _currentStepState.asStateFlow()

    private val _completedScanCount = MutableStateFlow(0)
    val completedScanCount: StateFlow<Int> = _completedScanCount.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(true) // Default to true for full preview support
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private val _autoSimulateSuccess = MutableStateFlow(true)
    val autoSimulateSuccess: StateFlow<Boolean> = _autoSimulateSuccess.asStateFlow()

    private val _feedbackTimeoutSeconds = MutableStateFlow(3)
    val feedbackTimeoutSeconds: StateFlow<Int> = _feedbackTimeoutSeconds.asStateFlow()

    private val _stepDelayMillis = MutableStateFlow(1000L)
    val stepDelayMillis: StateFlow<Long> = _stepDelayMillis.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<String>>(emptyList())
    val terminalLogs: StateFlow<List<String>> = _terminalLogs.asStateFlow()

    val pairedDevices: StateFlow<List<BluetoothDevice>> = bluetoothCommander.discoveredDevices

    val isBleMode: StateFlow<Boolean> = bluetoothCommander.isBleMode

    // CameraView-matched sequence delays
    private val _isBangleMode = MutableStateFlow(false)
    val isBangleMode: StateFlow<Boolean> = _isBangleMode.asStateFlow()

    private val _fluorescentOnDelay = MutableStateFlow(0)
    val fluorescentOnDelay: StateFlow<Int> = _fluorescentOnDelay.asStateFlow()

    private val _fluorescentOffDelay = MutableStateFlow(0)
    val fluorescentOffDelay: StateFlow<Int> = _fluorescentOffDelay.asStateFlow()

    private val _simulantOnDelay = MutableStateFlow(0)
    val simulantOnDelay: StateFlow<Int> = _simulantOnDelay.asStateFlow()

    private val _shortPhosphoOnDelay = MutableStateFlow(1)
    val shortPhosphoOnDelay: StateFlow<Int> = _shortPhosphoOnDelay.asStateFlow()

    private val _shortPhosphoOffDelay = MutableStateFlow(0)
    val shortPhosphoOffDelay: StateFlow<Int> = _shortPhosphoOffDelay.asStateFlow()

    private val _selectedDevice = MutableStateFlow<BluetoothDevice?>(null)
    val selectedDevice: StateFlow<BluetoothDevice?> = _selectedDevice.asStateFlow()

    val connectionState: StateFlow<BTConnectionState> = bluetoothCommander.connectionState
    val connectionError: StateFlow<String?> = bluetoothCommander.errorMessage

    // Steps list matching captureNextImage structure
    val steps = listOf(
        ScanStep(0, "White Light", listOf("a"), "c", "Sends 'a', waits for feedback, then sends 'c' off."),
        ScanStep(1, "Fluorescent Light", listOf("j"), "k", "Sends 'j', waits for feedback, then sends 'k' off."),
        ScanStep(2, "Simulant Light", listOf("d"), "f", "Sends 'd', waits for feedback, then sends 'f' off."),
        ScanStep(3, "Short Phospho", listOf("g"), null, "Sends 'g' to charge phosphorescence, waits for feedback."),
        ScanStep(4, "Super Light", listOf("g"), "h", "Sends 'g', waits for feedback, then sends 'h' off."),
        ScanStep(5, "Long Phospho", emptyList(), null, "Passive decay step, completes instantly.")
    )

    // Flow for capturing feedback events (both real BT and manual simulation)
    private val feedbackFlow = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 16)

    private var scanJob: Job? = null
    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    init {
        // Refresh paired devices
        refreshPairedDevices()

        // Listen for incoming Bluetooth bytes and convert to feedback signals
        viewModelScope.launch {
            bluetoothCommander.incomingData.collect { data ->
                addLog("[RX] Received data from device: \"$data\"")
                // Any received data acts as feedback trigger
                feedbackFlow.tryEmit(Unit)
            }
        }
    }

    fun setSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        addLog("Mode switched to: ${if (enabled) "SIMULATION (Demo)" else "REAL HARDWARE"}")
        if (enabled) {
            bluetoothCommander.disconnect()
        } else {
            refreshPairedDevices()
        }
    }

    fun setAutoSimulateSuccess(enabled: Boolean) {
        _autoSimulateSuccess.value = enabled
    }

    fun setFeedbackTimeout(seconds: Int) {
        _feedbackTimeoutSeconds.value = seconds.coerceIn(1, 15)
    }

    fun setStepDelay(millis: Long) {
        _stepDelayMillis.value = millis.coerceIn(100L, 5000L)
    }

    fun selectDevice(device: BluetoothDevice) {
        _selectedDevice.value = device
        addLog("Selected Bluetooth device: ${device.name ?: "Unknown"} [${device.address}]")
    }

    fun connectDevice() {
        val device = _selectedDevice.value
        if (device == null) {
            addLog("[ERROR] No Bluetooth device selected!")
            return
        }
        addLog("Connecting to Bluetooth device: ${device.name}...")
        bluetoothCommander.connect(device)
    }

    fun disconnectDevice() {
        addLog("Disconnecting Bluetooth device...")
        bluetoothCommander.disconnect()
    }

    fun setBleMode(enabled: Boolean) {
        bluetoothCommander.setBleMode(enabled)
        addLog("Bluetooth mode switched to: ${if (enabled) "BLE" else "CLASSIC (SPP)"}")
    }

    fun setBangleMode(enabled: Boolean) {
        _isBangleMode.value = enabled
        addLog("Bangle Mode set to: $enabled")
    }

    fun setFluorescentOnDelay(seconds: Int) {
        _fluorescentOnDelay.value = seconds.coerceIn(0, 10)
    }

    fun setFluorescentOffDelay(seconds: Int) {
        _fluorescentOffDelay.value = seconds.coerceIn(0, 10)
    }

    fun setSimulantOnDelay(seconds: Int) {
        _simulantOnDelay.value = seconds.coerceIn(0, 10)
    }

    fun setShortPhosphoOnDelay(seconds: Int) {
        _shortPhosphoOnDelay.value = seconds.coerceIn(0, 10)
    }

    fun setShortPhosphoOffDelay(seconds: Int) {
        _shortPhosphoOffDelay.value = seconds.coerceIn(0, 10)
    }

    fun getWarmupDelayMillis(stepIndex: Int): Long {
        val baseDelay = when (stepIndex) {
            0 -> 0L // White Light
            1 -> (_fluorescentOnDelay.value + 1) * 1000L // Fluorescent Light
            2 -> (_fluorescentOffDelay.value + _simulantOnDelay.value) * 1000L // Simulant Light
            3 -> _shortPhosphoOnDelay.value * 1000L // Short Phospho
            4 -> 0L // Super Light
            5 -> _shortPhosphoOffDelay.value * 1000L // Long Phospho
            else -> 0L
        }
        val padding = if (_isBangleMode.value) 2000L else 500L
        return baseDelay + padding
    }

    fun refreshPairedDevices() {
        bluetoothCommander.refreshDevices()
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothCommander.isBluetoothEnabled()
    }

    fun resetCount() {
        _completedScanCount.value = 0
        addLog("Scan count has been reset to 0.")
    }

    fun clearLogs() {
        _terminalLogs.value = emptyList()
    }

    /**
     * Start the sequence of 7 steps and continue scanning indefinitely in a loop.
     */
    fun startScan() {
        if (_scanState.value == ScanState.SCANNING) return

        // Verify connection if in real hardware mode
        if (!_isSimulationMode.value && bluetoothCommander.connectionState.value != BTConnectionState.CONNECTED) {
            addLog("[ERROR] Cannot start scan: Bluetooth device is not connected.")
            triggerAlert("No Connected Device", "Please connect a Bluetooth device or turn on Simulation Mode to test the app.")
            return
        }

        addLog("==========================================")
        addLog("▶ STARTING AUTOMATED SEQUENCE SCAN")
        addLog("==========================================")
        
        _scanState.value = ScanState.SCANNING

        scanJob = viewModelScope.launch {
            try {
                while (_scanState.value == ScanState.SCANNING) {
                    val cycleNumber = _completedScanCount.value + 1
                    addLog("--- Beginning Scan Loop #$cycleNumber ---")

                    var allStepsSucceeded = true

                    for (step in steps) {
                        if (_scanState.value != ScanState.SCANNING) break

                        _currentStepIndex.value = step.index
                        addLog("[STEP ${step.index + 1}/${steps.size}] Entering step: ${step.name}")

                        // Step execution:
                        val success = executeStep(step)
                        if (!success) {
                            allStepsSucceeded = false
                            break // Stop scan immediately upon timeout/failure
                        }
                    }

                    if (allStepsSucceeded && _scanState.value == ScanState.SCANNING) {
                        _completedScanCount.value += 1
                        addLog("✔ SUCCESS: Loop #${cycleNumber} complete! Total count: ${_completedScanCount.value}")
                        delay(500) // Brief delay before next cycle
                    } else {
                        break // Loop broken due to error or user stop
                    }
                }
            } catch (e: Exception) {
                Log.e("MainViewModel", "Scan sequence cancelled or error occurred", e)
                addLog("[SYSTEM] Scan sequence error: ${e.localizedMessage}")
            } finally {
                if (_scanState.value == ScanState.SCANNING) {
                    _scanState.value = ScanState.IDLE
                }
            }
        }
    }

    /**
     * Stop the scanning sequence and turn off any active signals.
     */
    fun stopScan() {
        if (_scanState.value != ScanState.SCANNING) return
        addLog("■ SCAN STOPPED BY USER")
        _scanState.value = ScanState.IDLE
        scanJob?.cancel()
        scanJob = null
        _currentStepState.value = StepState.PENDING
    }

    /**
     * Executes a single step's ON commands, awaits feedback, and then executes its OFF commands.
     */
    private suspend fun executeStep(step: ScanStep): Boolean {
        // 1. Send ON commands (if any)
        _currentStepState.value = StepState.SENDING_ON
        feedbackFlow.resetReplayCache() // Clear legacy/fast feedback before command is sent

        if (step.onCommands.isNotEmpty()) {
            for (cmd in step.onCommands) {
                sendBluetoothCommand(cmd, isOn = true)
            }
        }

        // 2. Warmup delay matching CameraView capture delay formula
        val warmupDelay = getWarmupDelayMillis(step.index)
        addLog("[STEP ${step.index + 1}] Warmup/Capture Delay: ${warmupDelay}ms...")
        delay(warmupDelay)

        // 3. Wait for feedback with timeout (only if commands were sent)
        if (step.onCommands.isNotEmpty()) {
            _currentStepState.value = StepState.WAITING_ON_FEEDBACK
            val onFeedbackReceived = waitForFeedbackWithTimeout(step, "ON")
            if (!onFeedbackReceived) {
                handleSequenceFailure(step, "ON")
                return false
            }
        }

        _currentStepState.value = StepState.SUCCESS
        delay(_stepDelayMillis.value) // Wait user-defined delay before turning off

        // 4. Send OFF command if defined
        if (step.offCommand != null) {
            _currentStepState.value = StepState.SENDING_OFF
            feedbackFlow.resetReplayCache() // Clear feedback cache before sending OFF command
            sendBluetoothCommand(step.offCommand, isOn = false)

            _currentStepState.value = StepState.WAITING_OFF_FEEDBACK
            val offFeedbackReceived = waitForFeedbackWithTimeout(step, "OFF")
            if (!offFeedbackReceived) {
                handleSequenceFailure(step, "OFF")
                return false
            }
            _currentStepState.value = StepState.SUCCESS
            delay(200) // Brief recovery delay between steps
        }

        return true
    }

    private var lastSentCommand: String? = null

    private fun getSimulatedFeedback(cmd: String?): String {
        return when (cmd) {
            "a" -> "WHITE ON"
            "c" -> "WHITE OFF"
            "d" -> "Simulant ON"
            "f" -> "Simulant OFF"
            "g" -> "ShortPhosparacence ON"
            "h" -> "ShortPhosparacence OFF"
            "j" -> "FLOURO ON"
            "k" -> "FLOURO OFF"
            "m" -> "Charging OFF"
            "n" -> "Charging ON"
            "z" -> "STEPPER 1/6"
            "x" -> "STEPPER 1/4"
            "s" -> "STEPPER START"
            "p" -> "STEPPER PAUSE"
            else -> "OK"
        }
    }

    /**
     * Dispatches Bluetooth commands depending on mode (Real vs. Simulated).
     */
    private fun sendBluetoothCommand(command: String, isOn: Boolean) {
        val label = if (isOn) "ON" else "OFF"
        lastSentCommand = command
        if (_isSimulationMode.value) {
            addLog("[SIM-TX] Sent $label Command: '$command'")
            
            // Auto-trigger simulated feedback in background if auto-simulation is enabled
            if (_autoSimulateSuccess.value) {
                viewModelScope.launch {
                    delay(800) // Simulate transmission and hardware processing delay
                    simulateFeedback()
                }
            }
        } else {
            addLog("[TX] Sent $label Command: '$command'")
            val success = bluetoothCommander.sendData(command)
            if (!success) {
                addLog("[ERROR] Failed to send Bluetooth command '$command'")
            }
        }
    }

    /**
     * Wait for feedback flow to trigger, timing out after the designated duration.
     */
    private suspend fun waitForFeedbackWithTimeout(step: ScanStep, stage: String): Boolean {
        addLog("... Awaiting feedback for $stage stage of Step ${step.index + 1} (${_feedbackTimeoutSeconds.value}s timeout) ...")
        
        val result = withTimeoutOrNull((_feedbackTimeoutSeconds.value * 1000).toLong()) {
            feedbackFlow.first()
        }
        return result != null
    }

    /**
     * Trigger manual simulation feedback.
     */
    fun simulateFeedback() {
        viewModelScope.launch {
            if (_currentStepState.value == StepState.WAITING_ON_FEEDBACK || 
                _currentStepState.value == StepState.WAITING_OFF_FEEDBACK) {
                val simResp = getSimulatedFeedback(lastSentCommand)
                addLog("[SIM-RX] Simulated response received: \"$simResp\"")
                feedbackFlow.emit(Unit)
            } else {
                addLog("[SIM-RX] Feedback simulated but step is not waiting for feedback.")
            }
        }
    }

    /**
     * Handle what happens when a timeout or failure is encountered.
     */
    private fun handleSequenceFailure(step: ScanStep, stage: String) {
        _currentStepState.value = StepState.TIMEOUT
        _scanState.value = ScanState.ALERT_NO_FEEDBACK
        scanJob?.cancel()
        scanJob = null

        addLog("[FAILURE] Feedback not received for $stage stage of Step ${step.index + 1} (${step.name})!")

        // 1. Play sound
        playAlarmSound()

        // 2. Vibrate device
        vibrateDevice()
    }

    fun dismissAlert() {
        _scanState.value = ScanState.IDLE
        _currentStepState.value = StepState.PENDING
        addLog("[ALERT] Warning dismissed. Ready to restart.")
    }

    private fun playAlarmSound() {
        try {
            // 1. Beep using ToneGenerator
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1500)

            // 2. Ringtone Alarm fallback
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(getApplication(), alarmUri)
            ringtone?.play()
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error playing alarm sound", e)
            addLog("[SYSTEM] Failed to play alarm sound: ${e.localizedMessage}")
        }
    }

    private fun vibrateDevice() {
        try {
            val vibrator = getApplication<Application>().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator?
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 200, 300, 200, 300), -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(longArrayOf(0, 300, 200, 300, 200, 300), -1)
                }
            }
        } catch (e: Exception) {
            Log.e("MainViewModel", "Error vibrating device", e)
        }
    }

    private fun addLog(message: String) {
        val timestamp = timeFormatter.format(Date())
        val formattedLog = "[$timestamp] $message"
        val currentList = _terminalLogs.value.toMutableList()
        currentList.add(formattedLog)
        // Keep logs size bounded
        if (currentList.size > 200) {
            currentList.removeAt(0)
        }
        _terminalLogs.value = currentList
        Log.d("ScanTerminal", formattedLog)
    }

    private fun triggerAlert(title: String, body: String) {
        // Log it so user sees it in terminal
        addLog("[SYSTEM-ALERT] $title: $body")
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothCommander.disconnect()
        scanJob?.cancel()
    }
}
