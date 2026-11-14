package com.aibuilt.pocketmasterai

import android.content.Context
import android.content.res.AssetManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterDevice
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterMidiManager
import com.aibuilt.pocketmasterai.data.local.PocketMasterDatabase
import com.aibuilt.pocketmasterai.data.local.dao.ChatDao
import com.aibuilt.pocketmasterai.data.local.dao.ToneSnapshotDao
import com.aibuilt.pocketmasterai.data.preference.ChatHistoryRepository
import com.aibuilt.pocketmasterai.data.preference.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pocketmaster_settings")

class AppContainer(private val appContext: Context) {
    val settingsRepository = SettingsRepository(appContext, appContext.dataStore)
    val chatHistoryRepository = ChatHistoryRepository(appContext)
    val deviceMapRepository = DeviceMapRepository()

    private val database by lazy {
        Room.databaseBuilder(
            appContext.applicationContext,
            PocketMasterDatabase::class.java,
            "pocketmaster.db"
        ).build()
    }

    val chatDao: ChatDao
        get() = database.chatDao()

    val toneSnapshotDao: ToneSnapshotDao
        get() = database.toneSnapshotDao()

    private val midiManager by lazy {
        PocketMasterMidiManager(appContext)
    }

    val deviceState: PocketMasterDevice
        get() = midiManager

    val deviceName: String
        get() = "Sonicake Pocket Master (USB OTG MIDI)"

    val assetManager: AssetManager
        get() = appContext.assets

    suspend fun loadDeviceMap(assetManager: AssetManager) {
        withContext(Dispatchers.IO) {
            try {
                val map = deviceMapRepository.loadFromAssets(assetManager)
                deviceMapRepository.setMap(map)
            } catch (e: Exception) {
                // Device map may not be present in a small build; device still functions
            }
        }
    }
}
