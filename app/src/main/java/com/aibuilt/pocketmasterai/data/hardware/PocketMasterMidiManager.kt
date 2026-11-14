package com.aibuilt.pocketmasterai.data.hardware

import android.content.Context
import android.media.midi.MidiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.hardware.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class PocketMasterMidiManager(private val context: Context) : PocketMasterDevice {

    companion object {
        private const val TAG = "PocketMasterMidi"
        private const val RETRY_DELAY_MS = 2000L
        private const val MIDISYNC_TIMEOUT = 5000L
    }

    private val midiManager = context.getSystemService(Context.MIDI_SERVICE) as MidiManager
    private val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

    private val _isConnected = MutableStateFlow(false)
    private val _deviceState = MutableStateFlow<PocketMasterDeviceState>(PocketMasterDeviceState())
    private val _connectionStatus = MutableStateFlow("Disconnected")
    private val commandQueue = Channel<ByteArray>(Channel.BUFFERED)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var midiDevice: MidiDevice? = null
    private var sender: MidiDevice.Sender? = null
    private var receiverThread: Thread? = null
    private var receiveBuffer = ByteArrayOutputStream()

    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    override val chain: StateFlow<IntArray> = _deviceState.map { it.chain }.asStateFlow()
    override val bypass: StateFlow<ByteArray> = _deviceState.map { it.bypass }.asStateFlow()
    override val models: StateFlow<shortArray> = _deviceState.map { it.models }.asStateFlow()
    override val params: StateFlow<HashMap<Int, HashMap<Int, Short>>> = _deviceState.map { it.params }.asStateFlow()
    override val name: StateFlow<String> = _deviceState.map { it.name }.asStateFlow()
    override val globalLevels: StateFlow<intArrayOf> = _deviceState.map { it.globalLevels }.asStateFlow()
    override val lastReceivedCommands: StateFlow<List<ByteArray>> = MutableStateFlow(emptyList()).asStateFlow()

    data class PocketMasterDeviceState(
        val chain: IntArray = IntArray(9) { i -> i },
        val bypass: ByteArray = ByteArray(9) { 0 },
        val models: ShortArray = ShortArray(9) { 0 },
        val params: HashMap<Int, HashMap<Int, Short>> = HashMap(),
        val name: String = "USER001",
        val globalLevels: intArrayOf = intArrayOf(0, 0, 0)
    )

    fun onMidiDeviceConnected(device: MidiDevice) {
        scope.launch {
            try {
                if (sender != null) {
                    sender?.close()
                }
                midiDevice = device
                sender = device.openSender(device.deviceId)
                _isConnected.value = true
                _connectionStatus.value = "Connected: ${device.name}"
                startReceiveThread()
                sendBytes(PocketMasterProtocol.editBufferDumpRequest())
                autoReconnectJob?.cancel()
                autoReconnectJob = null
            } catch (e: Exception) {
                Log.e(TAG, "Connect failed", e)
                _connectionStatus.value = "Connect failed: ${e.message}"
            }
        }
    }

    fun onMidiDeviceDisconnected(deviceId: Int) {
        Log.d(TAG, "Device disconnected: $deviceId")
        _isConnected.value = false
        _connectionStatus.value = "Disconnected"
        stopReceiveThread()
        autoReconnect()
    }

    private val autoReconnectJob = MutableStateFlow<Job?>(null)
    private fun autoReconnect() {
        autoReconnectJob.value = scope.launch {
            var attempts = 0
            while (attempts < 10) {
                delay(RETRY_DELAY_MS)
                val devices = midiManager.listDevices()
                for (device in devices) {
                    if (device.type == MidiDevice.TYPE_MIDI_OUT) {
                        onMidiDeviceConnected(device)
                        return@launch
                    }
                }
                attempts++
            }
            _connectionStatus.value = "No device found"
        }
    }

    private fun startReceiveThread() {
        stopReceiveThread()
        receiverThread = Thread {
            try {
                val device = midiManager.openDevice(sender?.device?.deviceId ?: return@Thread)
                val receiver = object : MidiMessageReceiver {
                    override fun receive(msg: MidiMessage) {
                        val data = msg.data
                        for (b in data) {
                            receiveBuffer.write(b.toInt() and 0xFF)
                        }
                        processReceivedData()
                    }
                }
                device.openReceiver(receiver)
                Thread.sleep(MIDISYNC_TIMEOUT)
                device.close()
            } catch (e: Exception) {
                Log.e(TAG, "Receiver error", e)
            }
        }
        receiverThread?.start()
    }

    private fun stopReceiveThread() {
        receiverThread?.interrupt()
        receiverThread = null
        receiveBuffer.reset()
    }

    private var pendingSysex = false
    private var sysexPayload = ByteArrayOutputStream()

    private fun processReceivedData() {
        while (receiveBuffer.available() > 0) {
            val b = receiveBuffer.read().toByte()
            when {
                !pendingSysex && b == PocketMasterProtocol.SYSEX_START.toByte() -> {
                    pendingSysex = true
                    sysexPayload.reset()
                    sysexPayload.write(b.toInt() and 0xFF)
                }
                pendingSysex && b == PocketMasterProtocol.SYSEX_END.toByte() -> {
                    pendingSysex = false
                    sysexPayload.write(b.toInt() and 0xFF)
                    handleSysExFrame(sysexPayload.toByteArray())
                }
                pendingSysex -> {
                    sysexPayload.write(b.toInt() and 0xFF)
                }
            }
        }
    }

    private fun handleSysExFrame(frame: ByteArray) {
        try {
            val decode = PocketMasterProtocol.decodeEditBufferDump(frame)
            if (decode != null && decode.isValid) {
                updateDeviceState(decode)
            }
            val cmd = frame.copyOfRange(0, frame.size)
            lastReceivedCommands.value = (lastReceivedCommands.value + cmd).takeLast(50)
            if (cmd.size > 3) {
                val op = cmd[3].toInt() and 0xFF
                handleCommandResponse(op, cmd)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Frame decode error", e)
        }
    }

    private fun updateDeviceState(dump: PocketMasterProtocol.EditBufferDump) {
        val data = dump.block0FirstBytes
        if (data.size < 14) return
        scope.launch(Dispatchers.Main) {
            val newModels = ShortArray(9)
            for (i in 0 until 9) {
                newModels[i] = ((data[i * 2].toInt() and 0x0F) shl 4) or (data[i * 2 + 1].toInt() and 0x0F)
            }
            val newBypass = ByteArray(9)
            for (i in 0 until 9) {
                newBypass[i] = data[18 + i].toByte()
            }
            val newState = PocketMasterDeviceState(
                chain = IntArray(9) { i -> i },
                bypass = newBypass,
                models = newModels,
                params = HashMap(),
                name = dump.name,
                globalLevels = intArrayOf(0, 0, 0)
            )
            _deviceState.value = newState
        }
    }

    private fun handleCommandResponse(op: Int, cmd: ByteArray) {
        when (op) {
            PocketMasterProtocol.OP_EDIT_BUFFER_DUMP -> {}
            PocketMasterProtocol.OP_SET_BLOCK_STATE -> {
                val block = cmd[4].toInt() and 0xFF
                val bypassed = cmd[5].toInt() and 0xFF == 1
                scope.launch(Dispatchers.Main) {
                    val current = _deviceState.value
                    val newBypass = current.bypass.copyOf()
                    newBypass[block] = if (bypassed) 1 else 0
                    _deviceState.value = current.copy(bypass = newBypass)
                }
            }
            PocketMasterProtocol.OP_SET_BLOCK_MODEL -> {
                val block = cmd[4].toInt() and 0xFF
                val mid = ((cmd[5].toInt() and 0xFF) shl 8) or (cmd[6].toInt() and 0xFF)
                scope.launch(Dispatchers.Main) {
                    val current = _deviceState.value
                    val newModels = current.models.copyOf()
                    newModels[block] = mid.toShort()
                    _deviceState.value = current.copy(models = newModels)
                }
            }
            PocketMasterProtocol.OP_SET_PARAMETER -> {
                val block = cmd[4].toInt() and 0xFF
                val pid = ((cmd[5].toInt() and 0xFF) shl 8) or (cmd[6].toInt() and 0xFF)
                val value = ((cmd[7].toInt() and 0xFF) shl 8) or (cmd[8].toInt() and 0xFF)
                scope.launch(Dispatchers.Main) {
                    val current = _deviceState.value
                    val newParams = current.params.toMutableMap()
                    newParams.getOrPut(block) { HashMap() }[pid] = value.toShort()
                    _deviceState.value = current.copy(params = newParams)
                }
            }
            PocketMasterProtocol.OP_CHAIN_REORDER -> {
                val newChain = IntArray(9)
                cmd.copyOfRange(4, 13).forEachIndexed { i, b -> newChain[i] = b.toInt() and 0xFF }
                scope.launch(Dispatchers.Main) {
                    _deviceState.value = _deviceState.value.copy(chain = newChain)
                }
            }
            PocketMasterProtocol.OP_SET_GLOBAL_LEVELS -> {
                scope.launch(Dispatchers.Main) {
                    _deviceState.value = _deviceState.value.copy(
                        globalLevels = intArrayOf(
                            cmd[4].toInt() and 0xFF,
                            cmd[5].toInt() and 0xFF,
                            cmd[6].toInt() and 0xFF
                        )
                    )
                }
            }
        }
    }

    override suspend fun connect(): Boolean {
        disconnect()
        val devices = midiManager.listDevices()
        for (device in devices) {
            if (device.type == MidiDevice.TYPE_MIDI_OUT) {
                onMidiDeviceConnected(device)
                return true
            }
        }
        autoReconnect()
        return false
    }

    override suspend fun disconnect() {
        stopReceiveThread()
        scope.launch {
            sender?.close()
            sender = null
            midiDevice?.close()
            midiDevice = null
            _isConnected.value = false
            _connectionStatus.value = "Disconnected"
            autoReconnectJob?.value?.cancel()
            autoReconnectJob.value = null
        }
    }

    override suspend fun sendBytes(data: ByteArray): Int {
        require(data.isNotEmpty())
        val sender = sender
        if (sender == null) {
            return 0
        }
        try {
            val packet = data.copyOf()
            sender.send(packet, System.currentTimeMillis())
            return packet.size
        } catch (e: Exception) {
            Log.e(TAG, "Send error", e)
            return 0
        }
    }

    override suspend fun receiveBytes(buffer: ByteArray): Int {
        return 0
    }

    override suspend fun processCommand(cmd: ByteArray): Boolean {
        return sendBytes(cmd) > 0
    }

    override fun toString(): String {
        return "PocketMasterMidiManager{connected=${_isConnected.value}, status=$_connectionStatus.value}"
    }
}
