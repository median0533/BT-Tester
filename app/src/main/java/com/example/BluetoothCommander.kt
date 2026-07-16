package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothSocket
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class BTConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    ERROR
}

class BluetoothCommander(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager?
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    // Classic Bluetooth references
    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null

    // BLE references
    private var bluetoothGatt: BluetoothGatt? = null
    private var rxCharacteristic: BluetoothGattCharacteristic? = null

    // Mode Toggle
    private val _isBleMode = MutableStateFlow(false)
    val isBleMode: StateFlow<Boolean> = _isBleMode.asStateFlow()

    private val _connectionState = MutableStateFlow(BTConnectionState.DISCONNECTED)
    val connectionState: StateFlow<BTConnectionState> = _connectionState

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    private val _incomingData = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingData: SharedFlow<String> = _incomingData

    // Discovered devices (combines paired classic + scanned BLE)
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.IO)
    private var readJob: Job? = null
    private var bleScanCallback: ScanCallback? = null
    private var isBleScanning = false

    companion object {
        private const val TAG = "BluetoothCommander"
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        // BLE UART UUIDs
        private val UART_SERVICE_UUID = UUID.fromString("c8b45206-84d5-4064-8cc3-246b823dc927")
        private val UART_RX_CHARACTERISTIC_UUID = UUID.fromString("db8e459b-e9f6-4fd8-9a7a-73afd97c77d0") // Write
        private val UART_TX_CHARACTERISTIC_UUID = UUID.fromString("93e802ff-7c1f-4962-a818-f5510858d3e5") // Notify
        private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }

    fun setBleMode(enabled: Boolean) {
        _isBleMode.value = enabled
        Log.d(TAG, "Bluetooth mode changed: ${if (enabled) "BLE" else "CLASSIC"}")
        refreshDevices()
    }

    /**
     * Get a list of currently paired (bonded) classic Bluetooth devices.
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return try {
            if (bluetoothAdapter?.isEnabled == true) {
                bluetoothAdapter.bondedDevices.toList()
            } else {
                emptyList()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Bluetooth permission not granted", e)
            emptyList()
        }
    }

    /**
     * Scan and discover nearby devices (classic paired + active BLE if BLE mode is active).
     */
    @SuppressLint("MissingPermission")
    fun refreshDevices() {
        // Stop any running scan first
        stopBleScan()

        val paired = getPairedDevices()
        val currentList = paired.toMutableList()
        _discoveredDevices.value = currentList

        // If BLE mode is enabled and Bluetooth is turned on, run a brief BLE scan to discover active BLE UART devices
        if (_isBleMode.value && bluetoothAdapter?.isEnabled == true) {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner != null) {
                isBleScanning = true
                Log.d(TAG, "Starting BLE scan to discover nearby devices...")
                
                val callback = object : ScanCallback() {
                    override fun onScanResult(callbackType: Int, result: ScanResult?) {
                        result?.device?.let { device ->
                            val address = device.address
                            synchronized(currentList) {
                                if (!currentList.any { it.address == address }) {
                                    currentList.add(device)
                                    _discoveredDevices.value = currentList.toList()
                                }
                            }
                        }
                    }
                    override fun onBatchScanResults(results: MutableList<ScanResult>?) {
                        results?.forEach { result ->
                            result.device?.let { device ->
                                val address = device.address
                                synchronized(currentList) {
                                    if (!currentList.any { it.address == address }) {
                                        currentList.add(device)
                                        _discoveredDevices.value = currentList.toList()
                                    }
                                }
                            }
                        }
                    }
                    override fun onScanFailed(errorCode: Int) {
                        Log.e(TAG, "BLE Scan failed with error code: $errorCode")
                    }
                }
                
                bleScanCallback = callback
                scanner.startScan(callback)

                // Stop scanning automatically after 5 seconds to conserve battery
                scope.launch {
                    kotlinx.coroutines.delay(5000)
                    stopBleScan()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        if (isBleScanning) {
            isBleScanning = false
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            val callback = bleScanCallback
            if (scanner != null && callback != null) {
                try {
                    scanner.stopScan(callback)
                    Log.d(TAG, "BLE Scan stopped.")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping BLE scan", e)
                }
            }
            bleScanCallback = null
        }
    }

    /**
     * Returns true if Bluetooth is enabled on the device.
     */
    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled ?: false
    }

    /**
     * Connect to a specific Bluetooth device (Classic or BLE depending on mode).
     */
    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        scope.launch {
            try {
                disconnect()
                _connectionState.value = BTConnectionState.CONNECTING
                _errorMessage.value = null

                if (_isBleMode.value) {
                    connectBle(device)
                } else {
                    connectClassic(device)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _errorMessage.value = "Connection failed: ${e.localizedMessage}"
                _connectionState.value = BTConnectionState.ERROR
                disconnect()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectClassic(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to Classic device: ${device.name ?: "Unnamed"} (${device.address})")
        socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        
        // Cancel discovery because it slows down connection
        bluetoothAdapter?.cancelDiscovery()

        socket?.connect()
        
        inputStream = socket?.inputStream
        outputStream = socket?.outputStream

        _connectionState.value = BTConnectionState.CONNECTED
        Log.d(TAG, "Successfully connected to RFCOMM SPP socket.")

        // Start listening for incoming feedback
        startReadingClassic()
    }

    @SuppressLint("MissingPermission")
    private fun connectBle(device: BluetoothDevice) {
        Log.d(TAG, "Connecting to BLE device: ${device.name ?: "Unnamed"} (${device.address})")
        bluetoothAdapter?.cancelDiscovery()
        stopBleScan()

        bluetoothGatt = device.connectGatt(context, false, object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        Log.d(TAG, "BLE connected, requesting MTU...")
                        gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_BALANCED)
                        gatt.requestMtu(512)
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        Log.d(TAG, "BLE disconnected.")
                        _connectionState.value = BTConnectionState.DISCONNECTED
                        disconnect()
                    }
                } else {
                    Log.e(TAG, "BLE GATT error status: $status")
                    _errorMessage.value = "GATT connection error code: $status"
                    _connectionState.value = BTConnectionState.ERROR
                    disconnect()
                }
            }

            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                Log.d(TAG, "BLE MTU changed to $mtu. Discovering services...")
                gatt.discoverServices()
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(UART_SERVICE_UUID)
                    if (service != null) {
                        rxCharacteristic = service.getCharacteristic(UART_RX_CHARACTERISTIC_UUID)
                        val txChar = service.getCharacteristic(UART_TX_CHARACTERISTIC_UUID)

                        if (txChar != null) {
                            gatt.setCharacteristicNotification(txChar, true)
                            val descriptor = txChar.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                            if (descriptor != null) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
                                } else {
                                    @Suppress("DEPRECATION")
                                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    @Suppress("DEPRECATION")
                                    gatt.writeDescriptor(descriptor)
                                }
                            }
                            _connectionState.value = BTConnectionState.CONNECTED
                            Log.d(TAG, "BLE UART Ready and Subscribed to TX notifications.")
                        } else {
                            Log.e(TAG, "TX UART Characteristic not found.")
                            _errorMessage.value = "TX UART characteristic not found."
                            _connectionState.value = BTConnectionState.ERROR
                            disconnect()
                        }
                    } else {
                        Log.e(TAG, "UART Service not found on this device.")
                        _errorMessage.value = "UART Service not found."
                        _connectionState.value = BTConnectionState.ERROR
                        disconnect()
                    }
                } else {
                    _errorMessage.value = "Service discovery failed with status $status"
                    _connectionState.value = BTConnectionState.ERROR
                    disconnect()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val dataBytes = characteristic.value ?: return
                val receivedString = String(dataBytes, Charsets.UTF_8)
                Log.d(TAG, "BLE RX (legacy): $receivedString")
                scope.launch {
                    _incomingData.emit(receivedString)
                }
            }

            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                val receivedString = String(value, Charsets.UTF_8)
                Log.d(TAG, "BLE RX: $receivedString")
                scope.launch {
                    _incomingData.emit(receivedString)
                }
            }

            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                Log.d(TAG, "BLE write callback status: $status")
            }
        })
    }

    /**
     * Send string data to the connected Bluetooth device.
     */
    @SuppressLint("MissingPermission")
    fun sendData(data: String): Boolean {
        if (_connectionState.value != BTConnectionState.CONNECTED) {
            Log.w(TAG, "Cannot send data, not connected")
            return false
        }

        if (_isBleMode.value) {
            val gatt = bluetoothGatt ?: return false
            val char = rxCharacteristic ?: return false
            return try {
                Log.d(TAG, "Sending BLE data: $data")
                val bytes = data.toByteArray(Charsets.UTF_8)
                char.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
                } else {
                    @Suppress("DEPRECATION")
                    char.value = bytes
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(char)
                }
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed BLE sendData", e)
                _errorMessage.value = "BLE write failed: ${e.localizedMessage}"
                _connectionState.value = BTConnectionState.ERROR
                disconnect()
                false
            }
        } else {
            val out = outputStream ?: return false
            return try {
                Log.d(TAG, "Sending Classic data: $data")
                out.write(data.toByteArray())
                out.flush()
                true
            } catch (e: IOException) {
                Log.e(TAG, "Failed Classic sendData", e)
                _errorMessage.value = "Write failed: ${e.localizedMessage}"
                _connectionState.value = BTConnectionState.ERROR
                disconnect()
                false
            }
        }
    }

    /**
     * Disconnect the connection and clean up resources.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        readJob?.cancel()
        readJob = null
        stopBleScan()

        // Classic close
        try {
            inputStream?.close()
        } catch (e: Exception) { /* Ignore */ }
        try {
            outputStream?.close()
        } catch (e: Exception) { /* Ignore */ }
        try {
            socket?.close()
        } catch (e: Exception) { /* Ignore */ }

        inputStream = null
        outputStream = null
        socket = null

        // BLE close
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: Exception) { /* Ignore */ }
        bluetoothGatt = null
        rxCharacteristic = null

        if (_connectionState.value != BTConnectionState.ERROR) {
            _connectionState.value = BTConnectionState.DISCONNECTED
        }
    }

    /**
     * Reads incoming feedback bytes from the serial Classic Bluetooth device.
     */
    private fun startReadingClassic() {
        readJob = scope.launch {
            val buffer = ByteArray(1024)
            var bytes: Int
            val input = inputStream ?: return@launch

            while (_connectionState.value == BTConnectionState.CONNECTED) {
                try {
                    bytes = input.read(buffer)
                    if (bytes > 0) {
                        val receivedString = String(buffer, 0, bytes)
                        Log.d(TAG, "Classic RX: $receivedString")
                        _incomingData.emit(receivedString)
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Classic read failed", e)
                    if (_connectionState.value == BTConnectionState.CONNECTED) {
                        _errorMessage.value = "Connection lost: ${e.localizedMessage}"
                        _connectionState.value = BTConnectionState.ERROR
                    }
                    disconnect()
                    break
                }
            }
        }
    }
}
