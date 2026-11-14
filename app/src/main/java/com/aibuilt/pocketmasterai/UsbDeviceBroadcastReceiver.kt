package com.aibuilt.pocketmasterai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbManager
import com.aibuilt.pocketmasterai.data.AppContainer

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
        val container = (context.applicationContext as? Application)?.let {
            (it as? AppApplication)?.instance?.let { app -> AppContainer(app) }
        } ?: return

        val device = if (attached) {
            val usbManager = context.getSystemService(Context.USB_SERVICE) as UsbManager
            val deviceList = usbManager.deviceList
            deviceList.values.firstOrNull()
        } else {
            null
        }

        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main + kotlinx.coroutines.SupervisorJob())
        scope.launch {
            if (attached && device != null) {
                container.deviceState.connect()
            } else {
                container.deviceState.disconnect()
            }
        }
    }
}
