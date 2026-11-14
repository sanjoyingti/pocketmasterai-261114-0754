package com.aibuilt.pocketmasterai.data.hardware

import android.content.Context
import android.hardware.usb.UsbConstants
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.media.midi.MidiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.ArrayDeque

class PocketMasterMidiManager(private val context: Context) : PocketMasterDevice {

    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager

    private val _isConnected = MutableStateFlow(false)
    private val _chain = MutableStateFlow(IntArray(9) { it })
    private val _bypass = MutableStateFlow(ByteArray(9) { 0 })
    private val _models = MutableStateFlow(ShortArray(9) { 0 })
    private val _name = MutableStateFlow("USER001")
    private val _globalLevels = MutableStateFlow(intArrayOf(0, 0, 0))

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val chain: StateFlow<IntArray> = _chain.asStateFlow()
    override val bypass: StateFlow<ByteArray> = _bypass.asStateFlow()
    override val models: StateFlow<ShortArray> = _models.asStateFlow()
    override val name: StateFlow<String> = _name.asStateFlow()
    override val globalLevels: StateFlow<IntArray> = _globalLevels.asStateFlow()

    private var connection: UsbDeviceConnection? = null
    private var endpoint: UsbEndpoint? = null
    private var inputStream: InputStream? = null
    private var outputStream: java.io.OutputStream? = null
    private var receiveJob: Job? = null
    private var device: UsbDevice? = null

    @OptIn(DelicateCoroutinesApi::class)
    override suspend fun connect(): Boolean {
        disconnect()
        val deviceList = usbManager.deviceList
        val midiDevices = midiManager.listDevices()
            .filter { it.deviceClass == MidiManager.DeviceClass.MIDI_AUDIO }
        var candidates = deviceList.values + midiDevices.mapNotNull { it.device }

        val target = candidates.find {
            it.deviceId == device?.deviceId || it.manufacturer?.contains("Sonicake", ignoreCase = true) == true
                || it.productName?.contains("Pocket", ignoreCase = true) == true
                || (it.classCode == UsbConstants.USB_CLASS_MIDIDEVICE && it.subclassCode == UsbConstants.USB_MIDICLASS_MIDI_STREAMING)
        } ?: return false

        if (!usbManager.hasPermission(target)) {
            emitPermissionRequest(target)
            return false
        }

        connection = usbManager.openDevice(target)
        device = target
        val interfaceObj = target.getInterface(0)
        endpoint = interfaceObj.getEndpoint(1) // OUT

        val success = connection?.requestInterface(interfaceObj.interfaceId, UsbConstants.USB_INTERFACE_EXCLUSIVE) == true
        if (!success) {
            connection?.close()
            connection = null
            return false
        }

        outputStream = connection?.getBulkTransferEndpoint(endpoint!!)
        inputStream = connection?.bulkTransferEndpoint(endpoint!!)?.inputStream

        _isConnected.value = true
        dumpState()
        startReceiveLoop()
        return true
    }

    override suspend fun disconnect() {
        receiveJob?.cancel()
        receiveJob = null
        outputStream?.close()
        inputStream?.close()
        connection?.releaseInterface()
        connection?.close()
        connection = null
        outputStream = null
        inputStream = null
        device = null
        _isConnected.value = false
    }

    override suspend fun sendBytes(data: ByteArray): Int {
        val out = outputStream ?: return 0
        out.write(data)
        out.flush()
        return data.size
    }

    override suspend fun receiveBytes(buffer: ByteArray): Int {
        val input = inputStream ?: return 0
        val read = input.read(buffer)
        return read
    }

    private fun emitPermissionRequest(device: UsbDevice) {
        val action = android.hardware.usb.action.UsbManager.ACTION_USB_PERMISSION
        val intent = android.content.Intent(action).apply {
            putExtra(android.hardware.usb.action.UsbManager.EXTRA_DEVICE, device)
            putExtra(android.hardware.usb.action.UsbManager.EXTRA_PERMISSION_GRANTED, true)
        }
        context.sendBroadcast(intent)
    }

    private var dumpJob: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    private fun dumpState() {
        dumpJob?.cancel()
        dumpJob = GlobalScope.launch(Looper.getMainLooper()) {
            while (_isConnected.value) {
                kotlinx.coroutines.delay(2000L)
                try {
                    sendBytes(PocketMasterProtocol.editBufferDumpRequest())
                } catch (e: Exception) {
                    _isConnected.value = false
                }
            }
        }
    }

    private var pendingReceives = ArrayDeque<ByteArray>()

    @OptIn(DelicateCoroutinesApi::class)
    private fun startReceiveLoop() {
        receiveJob = GlobalScope.launch {
            val buffer = ByteArray(1024)
            while (_isConnected.value) {
                try {
                    val input = inputStream ?: kotlinx.coroutines.delay(100L); continue
                    val read = input.read(buffer)
                    if (read > 0) {
                        val chunk = buffer.copyOf(read)
                        handleSysExChunk(chunk)
                    } else {
                        kotlinx.coroutines.delay(50L)
                    }
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    private fun handleSysExChunk(chunk: ByteArray) {
        val frameStart = chunk.indexOf(0xF0.toByte())
        if (frameStart < 0) return
        val frameEnd = chunk.indexOf(0xF7.toByte(), frameStart)
        if (frameEnd < 0) return
        val frame = chunk.copyOfRange(frameStart, frameEnd + 1)
        PocketMasterProtocol.decodeEditBufferDump(frame)?.let { dump ->
            _name.value = dump.name
        }
    }
}
