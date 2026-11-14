package com.aibuilt.pocketmasterai.data.preference

import android.content.Context
import com.aibuilt.pocketmasterai.data.local.ChatMessage
import com.aibuilt.pocketmasterai.data.local.ChatSession
import com.aibuilt.pocketmasterai.data.local.PocketMasterDatabase
import com.aibuilt.pocketmasterai.data.local.ToneSnapshot
import com.aibuilt.pocketmasterai.data.local.dao.ChatDao
import com.aibuilt.pocketmasterai.data.local.dao.ToneSnapshotDao
import kotlinx.coroutines.flow.Flow
import androidx.room.Room

class ChatHistoryRepository(context: Context) {
    private val database = Room.databaseBuilder(
        context.applicationContext,
        PocketMasterDatabase::class.java,
        "chat_history.db"
    ).build()

    val chatDao: ChatDao
        get() = database.chatDao()

    val toneSnapshotDao: ToneSnapshotDao
        get() = database.toneSnapshotDao()

    suspend fun createSession(title: String): Long {
        return database.insertSession(ChatSession(title = title))
    }

    suspend fun addMessage(sessionId: Long, role: String, content: String): Long {
        return database.insertMessage(ChatMessage(session_id = sessionId, role = role, content = content))
    }

    fun getMessagesBySession(sessionId: Long): Flow<List<ChatMessage>> =
        database.getMessagesBySession(sessionId)

    fun getAllSessions(): Flow<List<ChatSession>> = database.getAllSessions()

    suspend fun deleteSession(sessionId: Long) {
        database.deleteSession(sessionId)
    }

    suspend fun saveSnapshot(snapshotData: String) {
        database.insertSnapshot(ToneSnapshot(snapshotData = snapshotData))
    }

    fun getLatestSnapshot(): Flow<ToneSnapshot?> = database.getLatestSnapshot()

    suspend fun clearSnapshots() {
        database.clearSnapshots()
    }
}
