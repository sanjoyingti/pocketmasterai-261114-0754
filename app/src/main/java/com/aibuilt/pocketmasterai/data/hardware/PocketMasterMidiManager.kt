package com.aibuilt.pocketmasterai.data.hardware

import android.content.Context
import android.hardware.usb.UsbConstants.USB_CLASS_MIDI
import android.hardware.usb.UsbConstants.USB_CLASS_MIDI_AUDIO
import android.hardware.usb.UsbConstants.USB_CLASS_MIDI_COMM
import android.hardware.usb.UsbConstants.USB_CLASS_MIDI_STREAMING
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbInterface
import android.hardware.usb.UsbManager
import android.media.midi.MidiManager
import android.os.Build
import com.aibuilt.pocketmasterai.data.PocketMasterDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class PocketMasterMidiManager(private val context: Context) : PocketMasterDevice {

    private val usbmgr = context.getSystemService(Context.USB_SERVICE) as UsbManager
    private val middimgr = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val connectedPorts = CopyOnWriteArrayList<String>()

    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _deviceState = MutableStateFlow(PocketMasterDeviceState())
    override val chain: StateFlow<IntArray> = _deviceState.map { it.chain }.asStateFlow()
    override val bypass: StateFlow<ByteArray> = _deviceState.map { it.bypass }.asStateFlow()
    override val models: StateFlow<ShortArray> = _deviceState.map { it.models }.asStateFlow()
    override val name: StateFlow<String> = _deviceState.map { it.name }.asStateFlow()
    override val globalLevels: StateFlow<IntArray> = _deviceState.map { it.globalLevels }.asStateFlow()

    private val sendMutex = Mutex()

    override suspend fun connect(): Boolean {
        val midiDevices = middimgr.deviceList
        for (i in 0 until midiDevices.size) {
            val device = midiDevices.get(i)
            if (isPocketMasterCompatible(device)) {
                return openPort(device.deviceId)
            }
        }
        // Fallback: try all attached USB devices via UsbManager
        val attached = usbmgr.deviceList
        for ((name, dev) in attached) {
            if (isPocketMasterUsb(dev)) {
                if (usbmgr.hasPermission(dev)) {
                    return openUsbDevice(dev)
                }
            }
        }
        return false
    }

    private fun isPocketMasterCompatible(device: android.media.midi.MidiDevice): Boolean {
        return device.deviceInfo.vendorName.contains("sonicak", ignoreCase = true)
                || device.deviceInfo.productName.contains("pocket", ignoreCase = true)
                || device.deviceInfo.productName.contains("qme", ignoreCase = true)
                || device.deviceInfo.productName.contains("usb midi", ignoreCase = true)
                || device.deviceInfo.productName.contains("midi usb", ignoreCase = true)
    }

    private fun isPocketMasterUsb(dev: UsbDevice): Boolean {
        if (dev.productName == null) return false
        return dev.productName.contains("sonicak", ignoreCase = true)
                || dev.productName.contains("pocket", ignoreCase = true)
                || dev.productName.contains("qme", ignoreCase = true)
                || dev.productName.contains("usb midi", ignoreCase = true)
                || dev.productName.contains("midi usb", ignoreCase = true)
    }

    private fun openPort(deviceId: Int): Boolean {
        middimgr.openDevice(
            middimgr.getDevice(deviceId),
            object : MidiManager.OnDeviceConnectedListener {
                override fun onDeviceConnected(device: MidiManager.MidiDevice) {
                    val port = device.openInputPort(0)
                    if (port != null) {
                        connectedPorts.add(device.deviceId.toString())
                        _isConnected.value = true
                    }
                }

                override fun onDeviceDisconnected(device: MidiManager.MidiDevice) {
                    connectedPorts.remove(device.deviceId.toString())
                    _isConnected.value = false
                }
            },
            null
        )
        return true
    }

    private fun openUsbDevice(dev: UsbDevice): Boolean {
        val connection = usbmgr.openDevice(dev) ?: return false
        val portName = "${dev.deviceName}_midi"
        try {
            val port = middimgr.openUsbPort(connection, portName)
            connectedPorts.add(portName)
            _isConnected.value = true
        } catch (e: Exception) {
            connection.close()
        }
        return true
    }

    override suspend fun disconnect() {
        withContext(Dispatchers.Main) {
            for (portId in connectedPorts.toList()) {
                try {
                    if (portId.startsWith("[")) {
                        val deviceId = portId.removeSurrounding("[", "]").toIntOrNull() ?: continue
                        middimgr.closeDevice(middimgr.getDevice(deviceId))
                    } else {
                        middimgr.closeUsbPort(portId)
                    }
                } catch (_: Exception) {}
            }
            connectedPorts.clear()
            _isConnected.value = false
        }
    }

    override suspend fun sendBytes(data: ByteArray): Int {
        if (!_isConnected.value || data.isEmpty()) return 0
        sendMutex.withLock {
            return withContext(Dispatchers.Main) {
                var written = 0
                for (portId in connectedPorts) {
                    try {
                        if (portId.startsWith("[")) {
                            val deviceId = portId.removeSurrounding("[", "]").toIntOrNull() ?: continue
                            val device = middimgr.getDevice(deviceId)
                            if (device != null) {
                                val port = device.openInputPort(0)
                                written += port?.write(data) ?: 0
                                port?.close()
                            }
                        } else {
                            written += middimgr.sendControlMsg(portId, data, data.size)
                        }
                    } catch (e: Exception) {
                        // try next port
                    }
                }
                written
            }
        }
    }

    override suspend fun receiveBytes(buffer: ByteArray): Int {
        return 0
    }
}
