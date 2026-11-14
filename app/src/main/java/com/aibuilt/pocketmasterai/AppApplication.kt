package com.aibuilt.pocketmasterai

import android.app.Application

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
