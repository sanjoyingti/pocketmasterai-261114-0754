package com.aibuilt.pocketmasterai

import android.content.Context
import android.hardware.usb.UsbManager
import android.media.midi.MidiManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
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
        val container = remember { AppContainer(applicationContext) }
        val viewModel = ViewModelProvider(
            this,
            PocketMasterViewModel.Factory(container)
        )[PocketMasterViewModel::class]
        setContent {
            PocketMasterTheme {
                PocketMasterApp(container = container, viewModel = viewModel)
            }
        }
    }
}
