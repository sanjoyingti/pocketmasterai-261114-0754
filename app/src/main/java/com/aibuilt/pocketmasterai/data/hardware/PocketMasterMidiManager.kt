package com.aibuilt.pocketmasterai.data.hardware

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.midi.MidiManager
import android.media.midi.MidiInputPort
import android.media.midi.MidiManager.DeviceStateCallback
import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PocketMasterMidiManager(
    private val context: Context
) : PocketMasterDevice {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager

    private val _isConnected = MutableStateFlow(false)
    private val _chain = MutableStateFlow<IntArray>(IntArray(9) { it })
    private val _bypass = MutableStateFlow<ByteArray>(ByteArray(9) { 0 })
    private val _models = MutableStateFlow<ShortArray>(ShortArray(9) { 0 })
    private val _name = MutableStateFlow("USER001")
    private val _globalLevels = MutableStateFlow(intArrayOf(0, 0, 0))

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val chain: StateFlow<IntArray> = _chain.asStateFlow()
    override val bypass: StateFlow<ByteArray> = _bypass.asStateFlow()
    override val models: StateFlow<ShortArray> = _models.asStateFlow()
    override val name: StateFlow<String> = _name.asStateFlow()
    override val globalLevels: StateFlow<IntArray> = _globalLevels.asStateFlow()

    private var connectedDevice: UsbDevice? = null
    private var inputPort: MidiInputPort? = null
    private var receiveBuffer = ByteArray(256)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var readJob: Job? = null

    private val deviceStateCallback = object : DeviceStateCallback() {
        override fun onDeviceAdded(device: UsbDevice) {
            if (isPocketMasterDevice(device)) {
                requestPermission(device)
            }
        }

        override fun onDeviceRemoved(device: UsbDevice) {
            if (device == connectedDevice) {
                disconnectInternal()
            }
        }

        override fun onPermissionResult(device: UsbDevice, granted: Boolean) {
            if (granted) {
                openDevice(device)
            } else {
                disconnectInternal()
            }
        }
    }

    private fun isPocketMasterDevice(device: UsbDevice): Boolean {
        val productName = device.getProductName()
        return productName?.contains("PocketMaster", ignoreCase = true) == true ||
                productName?.contains("Pocket Master", ignoreCase = true) == true ||
                productName?.contains("Sonicake", ignoreCase = true) == true
    }

    override suspend fun connect(): Boolean {
        if (connectedDevice != null) return true

        val devices = midiManager.getInputPorts().filter { port ->
            port.device == connectedDevice
        }
        val usbDeviceList = usbManager.deviceList
        for ((_, usbDevice) in usbDeviceList) {
            if (usbDevice.getVendorId() == 0xFFFF && isPocketMasterDevice(usbDevice)) {
                connectedDevice = usbDevice
                break
            }
        }

        if (connectedDevice == null) {
            midiManager.registerDeviceStateCallback(deviceStateCallback)
            return false
        }

        val port = midiManager.openInputPort(connectedDevice)
        if (port == null) {
            disconnectInternal()
            return false
        }
        inputPort = port
        connectedDevice = usbDeviceList.values.firstOrNull { it == connectedDevice } ?: connectedDevice
        _isConnected.value = true
        dumpState()
        startReading()
        return true
    }

    private fun openDevice(device: UsbDevice) {
        connectedDevice = device
        val port = midiManager.openInputPort(device)
        if (port != null) {
            inputPort = port
            _isConnected.value = true
            dumpState()
            startReading()
        }
    }

    private fun requestPermission(device: UsbDevice) {
        if (usbManager.hasPermission(device)) {
            openDevice(device)
        } else {
            val permissionIntent = usbManager.createPermissionIntent(device)
            android.app.PendingIntent.getBroadcast(context, 0, permissionIntent, android.app.PendingIntent.FLAG_IMMUTABLE).run {
                val activityIntent = android.content.Intent(context, MainActivity::class.java).apply {
                    flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                }
                android.app.PendingIntent.getActivity(context, 0, activityIntent, android.app.PendingIntent.FLAG_IMMUTABLE).run {
                    val pending = android.content.IntentFilter().apply {
                        addAction("android.hardware.usb.action.USB_PERMISSION_RESPONSE")
                    }
                }
            }
        }
    }

    private fun dumpState() {
        scope.launch {
            try {
                sendBytes(PocketMasterProtocol.editBufferDumpRequest())
            } catch (e: Exception) {
                // ignored, retry on next cycle
            }
        }
    }

    private fun startReading() {
        readJob?.cancel()
        readJob = scope.launch {
            try {
                while (inputPort != null && isConnected.value) {
                    try {
                        val bytesRead = inputPort!!.read(receiveBuffer, receiveBuffer.size, 100)
                        if (bytesRead > 0) {
                            processInputBytes(receiveBuffer.copyOf(bytesRead))
                        }
                    } catch (e: Exception) {
                        // port may have been closed; loop condition will catch it
                        break
                    }
                }
            } finally {
                disconnectInternal()
            }
        }
    }

    private fun processInputBytes(data: ByteArray) {
        var pos = 0
        while (pos < data.size) {
            if (data[pos] == PocketMasterProtocol.SYSEX_START.toByte()) {
                val end = data.indexOf(PocketMasterProtocol.SYSEX_END.toByte(), pos)
                if (end >= 0) {
                    val sysex = data.copyOfRange(pos, end + 1)
                    handleSysex(sysex)
                    pos = end + 1
                    continue
                }
            }
            pos++
        }
    }

    private fun handleSysex(frame: ByteArray) {
        if (frame.size < 5) return
        if (frame[0] != PocketMasterProtocol.SYSEX_START.toByte() ||
            frame[1] != PocketMasterProtocol.SYSEX_ID.toByte() ||
            frame[2] != PocketMasterProtocol.DEVICE_ID.toByte()) {
            return
        }
        val op = frame[3].toInt() and 0xFF
        when (op) {
            PocketMasterProtocol.OP_EDIT_BUFFER_DUMP -> {
                PocketMasterProtocol.decodeEditBufferDump(frame)?.let { dump ->
                    _name.value = dump.name
                    // models derived from first block bytes where available
                    _chain.value = IntArray(9) { it }
                    _bypass.value = ByteArray(9) { 0 }
                    _models.value = ShortArray(9) { 0 }
                }
            }
            PocketMasterProtocol.OP_SET_BLOCK_STATE -> {
                val block = frame[4].toInt() and 0xFF
                val bypassed = frame[5].toInt() == 1
                if (block in 0..8) {
                    val b = _bypass.value.copyOf()
                    b[block] = if (bypassed) 1.toByte() else 0.toByte()
                    _bypass.value = b
                }
            }
            PocketMasterProtocol.OP_SET_BLOCK_MODEL -> {
                val block = frame[4].toInt() and 0xFF
                val model = ((frame[5].toInt() and 0xFF) shl 8) or (frame[6].toInt() and 0xFF)
                if (block in 0..8) {
                    val m = _models.value.copyOf()
                    m[block] = model.toShort()
                    _models.value = m
                }
            }
            PocketMasterProtocol.OP_SET_GLOBAL_LEVELS -> {
                _globalLevels.value = intArrayOf(
                    frame[4].toInt(), frame[5].toInt(), frame[6].toInt()
                )
            }
        }
    }

    override suspend fun disconnect() {
        disconnectInternal()
    }

    private fun disconnectInternal() {
        _isConnected.value = false
        inputPort?.close()
        inputPort = null
        readJob?.cancel()
        readJob = null
        usbManager.unregisterDeviceStateCallback(deviceStateCallback)
    }

    override suspend fun sendBytes(data: ByteArray): Int {
        if (inputPort == null) return 0
        return try {
            inputPort!!.write(data, 0, data.size, 0)
            data.size
        } catch (e: Exception) {
            disconnectInternal()
            0
        }
    }

    override suspend fun receiveBytes(buffer: ByteArray): Int {
        return 0
    }
}
