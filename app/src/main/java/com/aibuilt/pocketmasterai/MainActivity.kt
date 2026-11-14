package com.aibuilt.pocketmasterai

import android.content.Context
import android.hardware.usb.UsbManager
import android.media.midi.MidiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.rememberNavController
import com.aibuilt.pocketmasterai.ui.theme.PocketMasterTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val usbManager by lazy {
        getSystemService(Context.USB_SERVICE) as UsbManager
    }

    private val midiManager by lazy {
        getSystemService(Context.MIDI_SERVICE) as MidiManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = remember { AppContainer(applicationContext) }
            val viewModel = ViewModelProvider(
                this,
                PocketMasterViewModel.Factory(container)
            )[PocketMasterViewModel::class]
            PocketMasterTheme {
                PocketMasterApp(container = container, viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // request device discovery on resume (real USB MIDI device or simulator)
        val viewModel = ViewModelProvider(
            this,
            PocketMasterViewModel.Factory(AppContainer(applicationContext))
        )[PocketMasterViewModel::class]
        viewModel.requestDevice()
    }
}
