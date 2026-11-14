package com.aibuilt.pocketmasterai

import android.app.Application

class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: AppApplication
            private set
    }
}
