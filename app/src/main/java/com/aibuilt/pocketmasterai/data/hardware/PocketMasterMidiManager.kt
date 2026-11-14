package com.aibuilt.pocketmasterai.data.hardware

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelableReceivedIntentCallback
import android.hardware.usb.*
import android.media.midi.MidiDevice
import android.media.midi.MidiManager
import android.media.midi.MidiDeviceInfo
import androidx.core.content.ContextCompat
import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.ConcurrentLinkedQueue

class PocketMasterMidiManager(private val context: Context) : PocketMasterDevice {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val ioScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _isConnected = MutableStateFlow(false)
    private val _deviceState = MutableStateFlow(PocketMasterDeviceState())
    private val _connectionStatus = MutableStateFlow("Not connected")

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val chain: StateFlow<IntArray> = _deviceState.map { it.chain }.asStateFlow()
    override val bypass: StateFlow<ByteArray> = _deviceState.map { it.bypass }.asStateFlow()
    override val models: StateFlow<ShortArray> = _deviceState.map { it.models }.asStateFlow()
    override val name: StateFlow<String> = _deviceState.map { it.name }.asStateFlow()
    override val globalLevels: StateFlow<IntArray> = _deviceState.map { it.globalLevels }.asStateFlow()
    override val deviceState: StateFlow<PocketMasterDeviceState> = _deviceState.asStateFlow()

    private var midiDevice: MidiDevice? = null
    private var midiSender: MidiDevice.Sender? = null
    private var outputPort: UsbDeviceConnection? = null
    private var usbDevice: UsbDevice? = null

    private var outputSenderJob: Job? = null
    private var inputJob: Job? = null

    // Send queue to serialize SysEx packets with inter-packet delay
    private val sendQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isSending = false

    private val reconnectJob = Job()
    private var lastConnectedDeviceId: String? = null
    private var lastConnectedIsUsb = false

    init {
        // listen for usb attachment/detachment
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat.registerReceiver(
                context,
                object : BroadcastReceiver() {
                    @SuppressLint("UnspecifiedReceiverFlag")
                    override fun onReceive(context: Context, intent: Intent) {
                        when (intent.action) {
                            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                                handleUsbAttached()
                            }
                            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                                disconnect()
                            }
                        }
                    }
                },
                filter,
                BroadcastReceiver.RECEIVER_NOT_EXPORTED
            )
        } else {
            val filter = IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
            ContextCompat.registerReceiver(
                context,
                object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) {
                        when (intent.action) {
                            UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                                handleUsbAttached()
                            }
                            UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                                disconnect()
                            }
                        }
                    }
                },
                filter
            )
        }
    }

    private fun handleUsbAttached() {
        // only auto-connect if previously used this device type and no other connection
        if (lastConnectedIsUsb && !_isConnected.value) {
            val connected = usbManager.deviceList.values.firstOrNull { d ->
                d.productName?.contains("Pocket Master", ignoreCase = true) == true ||
                        d.productName?.contains("QME", ignoreCase = true) == true ||
                        d.vendorId == USB_VENDOR_ID_SONICAKE
            }
            if (connected != null) {
                lastConnectedDeviceId = connected.deviceId.toString()
                requestDeviceAccess(connected)
            }
        }
    }

    companion object {
        private const val USB_VENDOR_ID_SONICAKE = 0x26AC
    }

    override suspend fun connect(): Boolean {
        _connectionStatus.value = "Scanning devices..."
        val connectedViaMidi = tryConnectMidi()
        if (connectedViaMidi) {
            lastConnectedDeviceId = null
            lastConnectedIsUsb = false
            return true
        }

        val candidates = mutableListOf<UsbDevice>()
        for (pair in usbManager.deviceList.values) {
            if (pair.productName?.contains("Pocket Master", ignoreCase = true) == true ||
                pair.productName?.contains("QME", ignoreCase = true) == true) {
                candidates.add(pair)
            }
        }
        // Fall back: any USB midi class device
        if (candidates.isEmpty()) {
            for (pair in usbManager.deviceList.values) {
                if (pair.isMidiDevice) candidates.add(pair)
            }
        }
        if (candidates.isEmpty()) {
            _connectionStatus.value = "No Pocket Master found"
            return false
        }
        val target = candidates.first()
        lastConnectedDeviceId = target.deviceId.toString()
        lastConnectedIsUsb = true

        return if (usbManager.hasPermission(target)) {
            openUsb(target)
        } else {
            tryRequestPermission(target)
            false
        }
    }

    private suspend fun tryConnectMidi(): Boolean {
        val devices = midiManager.deviceList.filter { it.isOutput }
        val target = devices.find { d ->
            d.info.productName?.contains("Pocket Master", ignoreCase = true) == true ||
                    d.info.productName?.contains("QME", ignoreCase = true) == true
        } ?: return false

        lastConnectedDeviceId = target.info.deviceId.toString()
        lastConnectedIsUsb = false

        return withContext(Dispatchers.IO) {
            val open = midiManager.openDevice(target, object : MidiManager.DeviceCallback() {
                override fun onDeviceOpened(device: MidiDevice) {
                    _device = device
                }

                override fun onDeviceClosed(device: MidiDevice) {
                    _device = null
                    _isConnected.value = false
                }
            }, null)
            if (!open) {
                _connectionStatus.value = "Open failed"
                false
            } else {
                _connectionStatus.value = "Connected via MIDI"
                true
            }
        }
    }

    private var _device: MidiDevice? = null
    private fun configureMidiSender(device: MidiDevice): Boolean {
        return try {
            outputSenderJob = ioScope.launch {
                try {
                    midiSender = device.openSender(0)
                    outputSenderJob = launch {
                        while (isActive && midiSender != null) {
                            val packet = sendQueue.poll()
                            if (packet == null) {
                                delay(10)
                                continue
                            }
                            sendQueue.add(packet)
                            withContext(Dispatchers.IO) {
                                midiSender!!.send(packet, System.nanoTime())
                            }
                            delay(20)
                        }
                    }
                    true
                } catch (e: Exception) {
                    _connectionStatus.value = "MIDI open failed: ${e.message}"
                    false
                }
            }
            true
        } catch (e: Exception) {
            _connectionStatus.value = "MIDI error: ${e.message}"
            false
        }
    }

    private fun openUsb(device: UsbDevice): Boolean {
        return try {
            outputPort = usbManager.openDevice(device) ?: return false
            this.usbDevice = device

            // claim interfaces
            var claimed = false
            for (i in 0 until device.interfaceCount) {
                if (usbManager.claimInterface(device, i, true)) claimed = true
            }
            if (!claimed) return false

            _connectionStatus.value = "Connected via USB OTG"
            startUsbInput()
            true
        } catch (e: Exception) {
            _connectionStatus.value = "USB open failed: ${e.message}"
            disconnect()
            false
        }
    }

    @Volatile
    private var usbInputOpen = false
    private fun startUsbInput() {
        if (usbInputOpen) return
        usbInputOpen = true
        outputJob = ioScope.launch {
            try {
                while (usbInputOpen && isActive) {
                    val buffer = ByteArray(64)
                    val read = outputPort!!.transfer(outputPort!!.getOutputEndpoint(0), buffer, buffer.size, 300)
                    if (read > 0) {
                        val frame = buffer.copyOf(read)
                        processReceivedFrame(frame)
                    }
                }
            } catch (e: Exception) {
                _connectionStatus.value = "USB read error: ${e.message}"
                _isConnected.value = false
            } finally {
                usbInputOpen = false
            }
        }
    }

    private fun processReceivedFrame(frame: ByteArray) {
        try {
            if (frame.size >= 4 && frame[0].toInt() and 0xFF == 0xF0 &&
                frame[1].toInt() and 0xFF == 0x7E && frame[2].toInt() and 0xFF == 0x53) {
                val decoded = PocketMasterProtocol.decodeEditBufferDump(frame)
                if (decoded != null) {
                    _deviceState.value = PocketMasterDeviceState(
                        chain = IntArray(9) { it },
                        bypass = ByteArray(9) { 0 },
                        models = ShortArray(9) { 0 },
                        name = decoded.name,
                        globalLevels = intArrayOf(0, 0, 0)
                    )
                }
            }
        } catch (e: Exception) {
            // ignore decode errors
        }
    }

    private fun requestPermissionResult(device: UsbDevice, granted: Boolean) {
        if (granted) {
            _connectionStatus.value = "Permission granted, connecting..."
            openUsb(device)
        } else {
            _connectionStatus.value = "Permission denied"
            _isConnected.value = false
        }
    }

    private fun tryRequestPermission(device: UsbDevice) {
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_USB_PERMISSION).apply {
                putExtra(UsbManager.EXTRA_DEVICE, device)
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE
            } else {
                0
            }
        )
        usbManager.requestPermission(device, pendingIntent)

        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == ACTION_USB_PERMISSION) {
                        synchronized(this) {
                            val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                            val dev = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                            dev?.let { requestPermissionResult(it, granted) }
                        }
                    }
                }
            },
            IntentFilter(ACTION_USB_PERMISSION)
        )
    }

    companion object {
        const val ACTION_USB_PERMISSION = "com.aibuilt.pocketmasterai.USB_PERMISSION"
    }

    private fun sendViaMidi(data: ByteArray) {
        if (midiSender == null) return
        ioScope.launch {
            withContext(Dispatchers.IO) {
                midiSender!!.send(data, System.nanoTime())
            }
        }
    }

    private fun sendViaUsb(data: ByteArray) {
        if (usbInputOpen && outputPort != null) {
            val endpoint = outputPort!!.getOutputEndpoint(0)
            val written = outputPort!!.transfer(endpoint, data, data.size, 200)
            if (written < 0) {
                _connectionStatus.value = "USB send failed"
                _isConnected.value = false
            }
        }
    }

    private suspend fun enqueueSend(data: ByteArray) {
        sendQueue.offer(data)
    }

    override suspend fun sendBytes(data: ByteArray): Int {
        if (!_isConnected.value) return 0
        if (data.isEmpty()) return 0
        return when {
            !lastConnectedIsUsb -> {
                sendViaMidi(data)
                data.size
            }
            else -> {
                sendViaUsb(data)
                data.size
            }
        }
    }

    private var _midiDevice: MidiDevice? = null
    private val MidiDevice.Sender?
        get() = midiSender

    override suspend fun receiveBytes(buffer: ByteArray): Int {
        // USB input handled via transfer; nothing to push here for USB
        if (lastConnectedIsUsb) return 0
        val sender = midiSender ?: return 0
        try {
            val n = sender.receive(buffer, System.nanoTime())
            if (n > 0) processReceivedFrame(buffer.copyOf(n))
            return n
        } catch (e: Exception) {
            return 0
        }
    }

    override suspend fun disconnect() {
        try {
            usbInputOpen = false
            outputJob?.cancel()
            midiSender?.close()
            midiSender = null
            _device?.close()
            _device = null
            _midiDevice = null
            outputPort?.close()
            outputPort = null
            usbDevice = null
            _deviceState.value = PocketMasterDeviceState()
            _isConnected.value = false
            _connectionStatus.value = "Disconnected"
        } catch (e: Exception) {
            // ignore
        }
    }

    override fun onCleared() {
        super.onCleared()
        disconnect()
        ioScope.cancel()
    }

    private val outputJob: Job
        get() = outputSenderJob!!
}
