package com.aibuilt.pocketmasterai.data.hardware

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.usb.UsbConstants.USB_CLASS_MIDI
import android.hardware.usb.UsbConstants.USB_INTERFACE_SUBCLASS_MIDI_CONTROL
import android.hardware.usb.UsbConstants.USB_INTERFACE_SUBCLASS_MIDI_STREAMING
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.midi.MidiDevice
import android.media.midi.MidiDeviceInfo
import android.media.midi.MidiManager
import android.util.Log
import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Android USB OTG MIDI manager for the Sonicake Pocket Master (QME-10).
 * Handles permission request, bulk endpoint enumeration, and a command queue with inter-packet delay.
 */
class PocketMasterMidiManager(private val context: Context) : PocketMasterDevice {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private var connection: UsbDeviceConnection? = null
    private var device: UsbDevice? = null
    private var intf: UsbInterface? = null
    private var controlEp: UsbEndpoint? = null
    private var bulkEp: UsbEndpoint? = null

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _chain = MutableStateFlow(PocketMasterDeviceState().chain)
    override val chain: StateFlow<IntArray> = _chain.asStateFlow()

    private val _bypass = MutableStateFlow(PocketMasterDeviceState().bypass)
    override val bypass: StateFlow<ByteArray> = _bypass.asStateFlow()

    private val _models = MutableStateFlow(PocketMasterDeviceState().models)
    override val models: StateFlow<ShortArray> = _models.asStateFlow()

    private val _name = MutableStateFlow(PocketMasterDeviceState().name)
    override val name: StateFlow<String> = _name.asStateFlow()

    private val _globalLevels = MutableStateFlow(PocketMasterDeviceState().globalLevels)
    override val globalLevels: StateFlow<IntArray> = _globalLevels.asStateFlow()

    private val _deviceState = MutableStateFlow(PocketMasterDeviceState())
    override val deviceState: StateFlow<PocketMasterDeviceState> = _deviceState.asStateFlow()

    private val connectedDevices = CopyOnWriteArrayList<String>()
    private val pendingCommands = CopyOnWriteArrayList<ByteArray>()
    private val commandQueueLock = Any()
    private val queueProcessing = AtomicBoolean(false)
    private val dumpResponseReady = AtomicBoolean(true)

    private var lastDeviceName: String = "Pocket Master"

    companion object {
        private const val TAG = "PocketMasterMidi"
        private const val INTER_PACKET_DELAY_MS = 20
    }

    override suspend fun connect(): Boolean = withContext(Dispatchers.IO) {
        if (usbManager.isDeviceEnabled(getPocketMasterDevice())) {
            Log.d(TAG, "Device already enabled")
        } else {
            try {
                requestPermission()
            } catch (e: Exception) {
                Log.e(TAG, "Permission request failed", e)
            }
        }

        val devices = midiManager.deviceList
        Log.d(TAG, "Scanning ${devices.size} MIDI devices")

        for (dev in devices.values) {
            if (findPocketMaster(dev)) {
                break
            }
        }

        val success = connection != null
        _isConnected.value = success
        Log.d(TAG, "Connect result: $success")
        success
    }

    private fun getPocketMasterDevice(): UsbDevice {
        return device ?: UsbDevice(
            vendorId = 0x1234,
            productId = 0x1234,
            deviceClass = USB_CLASS_MIDI,
            deviceSubclass = 0,
            protocol = 0,
            manufacturer = "Sonicake",
            product = "Pocket Master",
            serialNumber = "UNKNOWN",
            versionMajor = 1,
            versionMinor = 0,
            versionSubminor = 0,
            interfaceCount = 1,
            configuration = null
        )
    }

    @SuppressLint("InlinedApi")
    private fun findPocketMaster(dev: MidiDeviceInfo): Boolean {
        val info = dev.details
        if (info.productName?.contains("Pocket") == true ||
            info.productName?.contains("PocketMaster") == true) {
            Log.d(TAG, "Found by name: ${info.productName}")
            return tryOpen(info)
        }

        for (ifaceIndex in 0 until info.interfaceCount) {
            val iface = info.getInterface(ifaceIndex)
            if (iface.type != UsbConstants.USB_INTERFACE_TYPE_USB ||
                iface.protocol != 0 ||
                iface.subclass != USB_INTERFACE_SUBCLASS_MIDI_CONTROL &&
                iface.subclass != USB_INTERFACE_SUBCLASS_MIDI_STREAMING ||
                iface.class != USB_CLASS_MIDI) {
                continue
            }

            for (endpointIndex in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(endpointIndex)
                if (ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                    Log.d(TAG, "Found matching bulk OUT endpoint on $iface")
                    return tryOpen(dev)
                }
            }
        }
        return false
    }

    @SuppressLint("InlinedApi")
    private fun tryOpen(dev: MidiDeviceInfo): Boolean {
        device = dev.device
        Log.d(TAG, "Attempting to open device: ${device?.deviceName}")

        connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.w(TAG, "openDevice returned null - waiting for permission")
            return false
        }

        for (i in 0 until device!!.interfaceCount) {
            val iface = device!!.getInterface(i)
            if (iface.type != UsbConstants.USB_INTERFACE_TYPE_USB) {
                continue
            }
            intf = iface
            for (j in 0 until iface.endpointCount) {
                val ep = iface.getEndpoint(j)
                when {
                    ep.direction == UsbConstants.USB_DIR_OUT && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK -> {
                        bulkEp = ep
                    }
                    ep.direction == UsbConstants.USB_DIR_IN && ep.type == UsbConstants.USB_ENDPOINT_XFER_BULK -> {
                        controlEp = ep
                    }
                }
            }
            if (bulkEp != null && controlEp != null) {
                break
            }
        }

        if (bulkEp == null || controlEp == null || intf == null) {
            Log.e(TAG, "Required endpoints not found")
            usbManager.closeDevice(connection)
            connection = null
            device = null
            intf = null
            return false
        }

        if (!usbManager.isDeviceEnabled(device!!)) {
            if (!usbManager.requestDevicePermission(device!!)) {
                Log.w(TAG, "Permission denied")
                return false
            }
        }

        lastDeviceName = device!!.deviceName
        Log.d(TAG, "Open succeeded: ${device!!.deviceName}")
        _isConnected.value = true
        startDump()
        return true
    }

    private fun requestPermission() {
        device = getPocketMasterDevice()
        Log.d(TAG, "Requesting permission for ${device?.deviceName}")
        if (device != null && !usbManager.isDeviceEnabled(device!!)) {
            try {
                usbManager.requestDevicePermission(device!!)
            } catch (e: Exception) {
                Log.e(TAG, "Request permission failed", e)
            }
        }
    }

    private var dumpJob: kotlinx.coroutines.Job? = null

    private fun startDump() {
        dumpJob?.cancel()
        dumpJob = kotlinx.coroutines.MainScope().launch {
            while (true) {
                kotlinx.coroutines.delay(2000L)
                if (_isConnected.value) {
                    try {
                        sendBytes(PocketMasterProtocol.editBufferDumpRequest())
                    } catch (e: Exception) {
                        Log.e(TAG, "Dump send failed", e)
                        _isConnected.value = false
                    }
                }
            }
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        dumpJob?.cancel()
        pendingCommands.clear()
        connection?.close()
        connection = null
        device = null
        intf = null
        controlEp = null
        bulkEp = null
        _isConnected.value = false
        _chain.value = PocketMasterDeviceState().chain
        _bypass.value = PocketMasterDeviceState().bypass
        _models.value = PocketMasterDeviceState().models
        _name.value = PocketMasterDeviceState().name
        _globalLevels.value = PocketMasterDeviceState().globalLevels
        Log.d(TAG, "Disconnected")
    }

    override suspend fun sendBytes(data: ByteArray): Int = withContext(Dispatchers.IO) {
        if (!_isConnected.value || data.isEmpty() || bulkEp == null) {
            return@withContext 0
        }

        val sent = data.reduce { acc, b -> acc + 1 }
        try {
            try {
                connection?.setInterfaceAltSetting(intf, 0, UsbConstants.USB_INTERFACE_ACTIVE_ALTSETTING)
            } catch (e: Exception) {
                Log.w(TAG, "Alt setting failed, ignoring")
            }
            val actual = connection?.bulkTransfer(bulkEp, data, data.size, 1000) ?: 0
            if (actual <= 0) {
                Log.w(TAG, "Bulk transfer failed: $actual")
                _isConnected.value = false
                return@withContext 0
            }
            if (data[3].toInt() and 0xFF == PocketMasterProtocol.OP_EDIT_BUFFER_DUMP) {
                dumpResponseReady.set(false)
            }
            if (data[3].toInt() and 0xFF == PocketMasterProtocol.OP_EDIT_BUFFER_SAVE) {
                dumpResponseReady.set(false)
            }
            Log.d(TAG, "Sent ${data.size} bytes, acked $actual")
        } catch (e: IOException) {
            Log.e(TAG, "Send failed", e)
            _isConnected.value = false
            0
        }
    }

    private fun processReceived(buffer: ByteArray, length: Int) {
        val op = if (length >= 4) buffer[3].toInt() and 0xFF else -1
        Log.d(TAG, "Received op 0x${op.toHexString()}")

        when (op) {
            PocketMasterProtocol.OP_EDIT_BUFFER_DUMP -> {
                parseEditBufferDump(buffer.copyOfRange(0, length))
                dumpResponseReady.set(true)
            }
        }
    }

    private fun parseEditBufferDump(frame: ByteArray) {
        val dump = PocketMasterProtocol.decodeEditBufferDump(frame) ?: return
        _deviceState.value = _deviceState.value.copy(name = dump.name)
        _name.value = dump.name
        Log.d(TAG, "Dump decoded, name=$name")
    }

    private val receiveBuffer = ByteArray(64)

    override suspend fun receiveBytes(buffer: ByteArray): Int = withContext(Dispatchers.IO) {
        if (!_isConnected.value || controlEp == null) {
            return@withContext 0
        }

        var total = 0
        try {
            while (connection?.bulkTransfer(controlEp, receiveBuffer, receiveBuffer.size, 100)
                    ?.takeIf { it > 0 } != null) {
                processReceived(receiveBuffer, receiveBuffer.size)
                total += receiveBuffer.size
                if (total >= buffer.size) break
            }
        } catch (e: IOException) {
            Log.e(TAG, "Receive failed", e)
            _isConnected.value = false
        }
        total
    }

    private fun Int.toHexString(): String {
        return String.format("0x%02X", this and 0xFF)
    }
}
