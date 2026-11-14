package com.aibuilt.pocketmasterai

import android.content.Context
import android.content.res.AssetManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import com.aibuilt.pocketmasterai.data.agent.WebSearchRepository
import com.aibuilt.pocketmasterai.data.agent.AiProviderRepository
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterDevice
import com.aibuilt.pocketmasterai.data.hardware.PocketMasterMidiManager
import com.aibuilt.pocketmasterai.data.hardware.FakePocketMasterDevice
import com.aibuilt.pocketmasterai.data.local.PocketMasterDatabase
import com.aibuilt.pocketmasterai.data.local.dao.ChatDao
import com.aibuilt.pocketmasterai.data.local.dao.ToneSnapshotDao
import com.aibuilt.pocketmasterai.data.preference.ChatHistoryRepository
import com.aibuilt.pocketmasterai.data.preference.DeviceMapRepository
import com.aibuilt.pocketmasterai.data.preference.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "pocketmaster_settings")

class AppContainer(appContext: Context) {
    private val context = appContext.applicationContext

    val settingsRepository = SettingsRepository(context, context.dataStore)
    val chatHistoryRepository = ChatHistoryRepository(context)
    val deviceMapRepository = DeviceMapRepository()
    val webSearchRepository = WebSearchRepository(context, context.dataStore)
    val aiProviderRepository = AiProviderRepository(context)

    private val database by lazy {
        Room.databaseBuilder(
            context,
            PocketMasterDatabase::class.java,
            "pocketmaster.db"
        ).build()
    }

    val chatDao: ChatDao
        get() = database.chatDao()

    val toneSnapshotDao: ToneSnapshotDao
        get() = database.toneSnapshotDao()

    private var _device: PocketMasterDevice = PocketMasterMidiManager(context)
        set(value) {
            field = value
        }

    var useSimulator: Boolean = false
        set(value) {
            field = value
            _device = if (value) FakePocketMasterDevice() else PocketMasterMidiManager(context)
        }

    val deviceState: PocketMasterDevice
        get() = _device

    val deviceName: String
        get() = if (useSimulator) "Fake PocketMaster Simulator" else "Sonicake Pocket Master (USB OTG MIDI)"

    val assetManager: AssetManager
        get() = context.assets

    suspend fun loadDeviceMap(assetManager: AssetManager) {
        withContext(Dispatchers.IO) {
            try {
                val map = deviceMapRepository.loadFromAssets(assetManager)
                deviceMapRepository.setMap(map)
            } catch (e: Exception) {
                // Device map may not be present; device still functions
            }
        }
    }
}
