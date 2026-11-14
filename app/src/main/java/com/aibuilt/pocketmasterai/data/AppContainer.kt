package com.aibuilt.pocketmasterai.data

import android.content.Context
import android.content.res.AssetManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterDevice
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterMidiManager
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterProtocol
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

    private val device: PocketMasterDevice by lazy {
        PocketMasterMidiManager(appContext)
    }

    val deviceState: PocketMasterDevice
        get() = device

    val deviceName: String
        get() = when (device) {
            is PocketMasterMidiManager -> "Real MIDI Device"
            is FakePocketMasterDevice -> "Fake PocketMaster Simulator"
            else -> "Unknown Device"
        }

    suspend fun setDeviceSimulator(enabled: Boolean) {
        if (enabled) {
            // Replace with simulator - requires re-instantiation in real app
        }
    }

    fun getDeviceMap(): DeviceMapRepository? = deviceMapRepository
}
