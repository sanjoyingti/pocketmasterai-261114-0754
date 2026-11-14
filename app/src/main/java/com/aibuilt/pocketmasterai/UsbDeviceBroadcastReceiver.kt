package com.aibuilt.pocketmasterai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.lifecycle.ViewModelProvider

class UsbDeviceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        fun register(context: Context, container: AppContainer) {
            val filter = IntentFilter().apply {
                addAction(android.hardware.usb.action.USB_DEVICE_ATTACHED)
                addAction(android.hardware.usb.action.USB_DEVICE_DETACHED)
                addAction(android.hardware.usb.action.USB_PERMISSION)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(this, filter)
            }
        }

        fun handleIntent(context: Context, container: AppContainer, intent: Intent) {
            when (intent.action) {
                android.hardware.usb.action.USB_DEVICE_ATTACHED -> {
                    container.useSimulator = false
                    val viewModel = ViewModelProvider(
                        context,
                        PocketMasterViewModel.Factory(container)
                    )[PocketMasterViewModel::class]
                    viewModel.requestDevice()
                }
                android.hardware.usb.action.USB_DEVICE_DETACHED -> {
                    val viewModel = ViewModelProvider(
                        context,
                        PocketMasterViewModel.Factory(container)
                    )[PocketMasterViewModel::class]
                    viewModel.disconnect()
                }
                android.hardware.usb.action.USB_PERMISSION -> {
                    // Permission request handled via UsbManager directly by the MidiManager flow.
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val container = AppContainer(context)
        handleIntent(context, container, intent)
    }
}
