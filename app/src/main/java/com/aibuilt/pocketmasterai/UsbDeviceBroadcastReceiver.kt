package com.aibuilt.pocketmasterai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager

class UsbDeviceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            android.hardware.usb.action.USB_DEVICE_ATTACHED -> {
                handleDeviceChange(context, true)
            }
            android.hardware.usb.action.USB_DEVICE_DETACHED -> {
                handleDeviceChange(context, false)
            }
        }
    }

    private fun handleDeviceChange(context: Context, attached: Boolean) {
        val app = (context.applicationContext as? Application)?.let {
            it as? AppApplication
        }
        if (app == null) return

        val container = AppContainer(context.applicationContext as Context)
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
        scope.launch {
            if (attached) {
                container.deviceState.connect()
            } else {
                container.deviceState.disconnect()
            }
        }
    }
}
