package com.aibuilt.pocketmasterai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import androidx.lifecycle.ViewModelProvider

class UsbDeviceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            android.hardware.usb.action.USB_DEVICE_ATTACHED -> {
                handleDeviceChange(context, true)
            }
            android.hardware.usb.action.USB_DEVICE_DETACHED -> {
                handleDeviceChange(context, false)
            }
            UsbManager.ACTION_USB_PERMISSION -> {
                // USB permission granted/denied handled via the app's pending intent
            }
        }
    }

    private fun handleDeviceChange(context: Context, attached: Boolean) {
        val app = context.applicationContext as? AppApplication ?: return

        // Register for USB permission callbacks
        val permissionIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, UsbDeviceBroadcastReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )
        val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager

        if (attached) {
            val viewModel = ViewModelProvider(
                app,
                PocketMasterViewModel.Factory(AppContainer(app))
            )[PocketMasterViewModel::class]
            viewModel.requestDevice()
        } else {
            val viewModel = ViewModelProvider(
                app,
                PocketMasterViewModel.Factory(AppContainer(app))
            )[PocketMasterViewModel::class]
            viewModel.disconnect()
        }
    }
}
