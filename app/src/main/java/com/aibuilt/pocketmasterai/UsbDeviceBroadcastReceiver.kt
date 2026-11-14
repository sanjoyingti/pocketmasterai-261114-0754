package com.aibuilt.pocketmasterai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build

class UsbDeviceBroadcastReceiver : BroadcastReceiver() {

    companion object {
        fun register(context: Context, container: AppContainer) {
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

        fun handleIntent(context: Context, container: AppContainer, intent: Intent) {
            when (intent.action) {
                android.hardware.usb.action.USB_DEVICE_ATTACHED -> {
                    container.useSimulator = false
                    val viewModel = PocketMasterViewModelFactory(container)[PocketMasterViewModel::class]
                    viewModel.requestDevice()
                }
                android.hardware.usb.action.USB_DEVICE_DETACHED -> {
                    val viewModel = PocketMasterViewModelFactory(container)[PocketMasterViewModel::class]
                    viewModel.disconnect()
                }
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        // This is only used as a fallback; the app registers the receiver in MainActivity
        // and calls handleIntent directly with the AppContainer available.
    }
}
