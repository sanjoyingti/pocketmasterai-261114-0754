package com.aibuilt.pocketmasterai.data.preference

import android.content.Context
import androidx.room.Room
import com.aibuilt.pocketmasterai.data.local.ChatMessage
import com.aibuilt.pocketmasterai.data.local.ChatSession
import com.aibuilt.pocketmasterai.data.local.PocketMasterDatabase
import com.aibuilt.pocketmasterai.data.local.ToneSnapshot
import com.aibuilt.pocketmasterai.data.local.dao.ChatDao
import com.aibuilt.pocketmasterai.data.local.dao.ToneSnapshotDao
import kotlinx.coroutines.flow.Flow

class ChatHistoryRepository(context: Context) {
    private val database by lazy {
        Room.databaseBuilder(
            context.applicationContext,
            PocketMasterDatabase::class.java,
            "chat_history.db"
        ).build()
    }

    val chatDao: ChatDao
        get() = database.chatDao()

    val toneSnapshotDao: ToneSnapshotDao
        get() = database.toneSnapshotDao()

    suspend fun createSession(title: String): Long {
        return chatDao.insertSession(ChatSession(title = title))
    }

    suspend fun addMessage(sessionId: Long, role: String, content: String): Long {
        return chatDao.insertMessage(ChatMessage(session_id = sessionId, role = role, content = content))
    }

    fun getMessagesBySession(sessionId: Long): Flow<List<ChatMessage>> =
        chatDao.getMessagesBySession(sessionId)

    fun getAllSessions(): Flow<List<ChatSession>> = chatDao.getAllSessions()

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteSession(sessionId)
    }

    suspend fun saveSnapshot(snapshotData: String) {
        toneSnapshotDao.insertSnapshot(ToneSnapshot(snapshotData = snapshotData))
    }

    fun getLatestSnapshot(): Flow<ToneSnapshot?> = toneSnapshotDao.getLatestSnapshot()

    suspend fun clearSnapshots() {
        toneSnapshotDao.clearSnapshots()
    }
}
