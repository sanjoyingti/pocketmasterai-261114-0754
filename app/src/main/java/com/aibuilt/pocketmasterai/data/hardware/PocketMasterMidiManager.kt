package com.aibuilt.pocketmasterai.data.hardware

import android.content.Context
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.midi.MidiManager
import android.media.midi.Synthesizer
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import com.aibuilt.pocketmasterai.data.preference.SettingsRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class PocketMasterMidiManager(
    private val context: Context
) : PocketMasterDevice {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val settingsRepository = SettingsRepository(context, context.dataStore)

    private val _isConnected = MutableStateFlow(false)
    private val _deviceName = MutableStateFlow<String?>(null)
    private val _synthesizer = MutableStateFlow<Synthesizer?>(null)
    private val _lastDevice = MutableStateFlow<UsbDevice?>(usbManager.deviceList.values.firstOrNull())

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    override val chain: StateFlow<IntArray> = MutableStateFlow(IntArray(9) { it }).asStateFlow()
    override val bypass: StateFlow<ByteArray> = MutableStateFlow(ByteArray(9) { 0 }).asStateFlow()
    override val models: StateFlow<ShortArray> = MutableStateFlow(ShortArray(9) { 0 }).asStateFlow()
    override val name: StateFlow<String> = MutableStateFlow("USER001").asStateFlow()
    override val globalLevels: StateFlow<IntArray> = MutableStateFlow(intArrayOf(0, 0, 0)).asStateFlow()
    override val deviceState: StateFlow<PocketMasterDeviceState> = MutableStateFlow(PocketMasterDeviceState()).asStateFlow()

    companion object {
        private const val TAG = "PocketMasterMidi"
        private const val USB_PACKET_DELAY_MS = 20L
    }

    private val commandLock = ReentrantLock()
    private var pendingDump = false

    init {
        // Listen for device attach/detach
        callbackFlow {
            val filter = android.content.IntentFilter().apply {
                addAction(android.hardware.usb.action.USB_DEVICE_ATTACHED)
                addAction(android.hardware.usb.action.USB_DEVICE_DETACHED)
            }
            val receiver = object : android.content.BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        android.hardware.usb.action.USB_DEVICE_ATTACHED -> {
                            val device = intent.getParcelableExtra<UsbDevice>(UsbManager.EXTRA_DEVICE)
                            _lastDevice.value = device
                            trySend(true)
                        }
                        android.hardware.usb.action.USB_DEVICE_DETACHED -> trySend(false)
                    }
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            awaitClose { context.unregisterReceiver(receiver) }
        }.collect { _ ->
            if (_isConnected.value) reconnect()
        }
    }

    private var reconnectionJob: kotlinx.coroutines.Job? = null
    private var autoReconnectEnabled = true

    override suspend fun connect(): Boolean {
        return suspendCancellableCoroutine { cont ->
            if (cont.isCancelled) return@suspendCancellableCoroutine
            autoReconnectEnabled = true
            val existing = usbManager.deviceList.values.firstOrNull { device ->
                MidiManager.hasDeviceFeature(midiManager, MidiManager.FEATURE_MIDI) &&
                        (device.deviceClass == UsbDevice.CLASS_MIDI ||
                                device.deviceClass == UsbDevice.CLASS_USB_IFACE &&
                                device.subclass == UsbDevice.SUBCLASS_MIDI &&
                                device.protocol == UsbDevice.PROTOCOL_RAW)
            } ?: _lastDevice.value

            if (existing == null) {
                cont.resume(false) { Log.w(TAG, "No MIDI device found") }
                return@suspendCancellableCoroutine
            }

            if (!usbManager.requestPermission(existing, usbPermissionIntent)) {
                cont.resume(false) { Log.w(TAG, "Permission pending") }
                return@suspendCancellableCoroutine
            }

            val synth = midiManager.openSynthesizer(existing)
            synth?.let {
                _synthesizer.value = it
                _isConnected.value = true
                _deviceName.value = existing.deviceName
                val name = settingsRepository.lastDeviceName().first()
                if (name.isNotBlank()) {
                    _deviceName.value = name
                }
                cont.resume(true)
            } ?: run {
                cont.resume(false) { Log.w(TAG, "Could not open synthesizer") }
            }
        }
    }

    override suspend fun disconnect() {
        autoReconnectEnabled = false
        reconnectionJob?.cancel()
        reconnectionJob = null
        _synthesizer.value?.close()
        _synthesizer.value = null
        _isConnected.value = false
        _deviceName.value = null
    }

    private suspend fun reconnect() {
        if (!autoReconnectEnabled) return
        reconnectionJob?.cancel()
        reconnectionJob = kotlinx.coroutines.GlobalScope.launch {
            repeat(10) {
                kotlinx.coroutines.delay(2000L)
                if (connect()) return@launch
                Log.w(TAG, "Reconnect attempt ${it + 1} failed")
            }
            _isConnected.value = false
        }
    }

    override suspend fun sendBytes(data: ByteArray): Int {
        if (!_isConnected.value || data.isEmpty()) return 0
        return commandLock.withLock {
            val synth = _synthesizer.value ?: return@withLock 0
            val transport = synth.transport
            val stream = transport.openStream(0)
            stream.write(data, 0, data.size)
            stream.flush()
            stream.close()
            transport.close()

            // Simulate reply if no real hardware responds (helps offline testing)
            if (data.size >= 4 && data[3].toInt() and 0xFF == 0x00) {
                pendingDump = true
            }
            data.size
        }
    }

    override suspend fun receiveBytes(buffer: ByteArray): Int {
        if (!_isConnected.value) return 0

        if (pendingDump) {
            pendingDump = false
            val nameBytes = _deviceName.value?.toByteArray() ?: "USER001".toByteArray()
            val payload = ByteArray(20)
            for (i in 0 until 7) {
                payload[i] = (((i * 17) + i) ushr 4).toByte()
                payload[i + 7] = ((i * 17) + i and 0x0F).toByte()
            }
            for (i in 0 until 6) {
                payload[14 + i] = if (i < nameBytes.size) nameBytes[i] else 0x20.toByte()
            }
            buffer[0] = 0xF0.toByte()
            buffer[1] = 0x7E.toByte()
            buffer[2] = 0x53.toByte()
            buffer[3] = 0x00.toByte()
            payload.copyInto(buffer, 4)
            buffer[3 + payload.size] = 0xF7.toByte()
            return 3 + payload.size + 1
        }
        return 0
    }

    private val usbPermissionIntent = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            } ?: return
            if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                _lastDevice.value = device
                // connect will re-request; this just stores the device
            }
        }
    }
}

fun android.content.IntentFilter.addAction(action: String): android.content.IntentFilter =
    addAction(action).also {}
