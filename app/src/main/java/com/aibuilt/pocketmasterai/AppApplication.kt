package com.aibuilt.pocketmasterai

import android.app.Application
import android.util.Log

class AppApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        @Volatile
        var instance: AppApplication? = null
            private set
    }
}
