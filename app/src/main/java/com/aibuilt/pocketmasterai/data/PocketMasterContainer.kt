package com.aibuilt.pocketmasterai.data

import android.content.Context

class PocketMasterContainer(private val context: Context) {

    private val fakeDevice = FakePocketMasterDevice()

    val device: PocketMasterDevice = fakeDevice

    val deviceName: String
        get() = "Simulator: Sonicake Pocket Master"
}
