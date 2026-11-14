package com.aibuilt.pocketmasterai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Build

class UsbDeviceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        fun register(context: Context, appContainer: AppContainer) {
            val filter = IntentFilter().apply {
                addAction(android.hardware.usb.action.USB_DEVICE_ATTACHED)
                addAction(android.hardware.usb.action.USB_DEVICE_DETACHED)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(this, filter)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            android.hardware.usb.action.USB_DEVICE_ATTACHED -> {
                appContainer.useSimulator = false
                val viewModel = PocketMasterViewModelFactory(appContainer)[PocketMasterViewModel::class]
                viewModel.requestDevice()
            }
            android.hardware.usb.action.USB_DEVICE_DETACHED -> {
                val viewModel = PocketMasterViewModelFactory(appContainer)[PocketMasterViewModel::class]
                viewModel.disconnect()
            }
        }
    }
}
