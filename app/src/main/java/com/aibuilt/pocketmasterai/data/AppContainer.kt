package com.aibuilt.pocketmasterai.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterDevice
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterMidiManager
import com.aibuilt.pocketmasterai.data.local.PocketMasterDatabase
import com.aibuilt.pocketmasterai.data.preference.ChatHistoryRepository
import com.aibuilt.pocketmasterai.data.preference.SettingsRepository
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pocketmaster_settings")

class AppContainer(private val appContext: Context) {
    val settingsRepository = SettingsRepository(appContext, appContext.dataStore)
    val chatHistoryRepository = ChatHistoryRepository(appContext)

    private val database by lazy {
        Room.databaseBuilder(
            appContext.applicationContext,
            PocketMasterDatabase::class.java,
            "pocketmaster.db"
        ).build()
    }

    private val device: PocketMasterDevice by lazy {
        PocketMasterMidiManager(appContext)
    }

    val deviceState: PocketMasterDevice
        get() = device
}
