DELETE

=== FILE: app/src/main/java/com/aibuilt/pocketmasterai/data/local/dao/ChatDao.kt ===
=== ABOUT: Fixed a duplicate PocketMasterDevice interface and restored the ViewModel that AgentScreen/AgentToolExecutor expect ===
package com.aibuilt.pocketmasterai.data.local.dao

import androidx.room.*
import com.aibuilt.pocketmasterai.data.local.ChatMessage
import com.aibuilt.pocketmasterai.data.local.ChatSession
import com.aibuilt.pocketmasterai.data.local.PocketMasterDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_session ORDER BY created_at DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_message WHERE session_id = :sessionId ORDER BY created_at ASC")
    fun getMessagesBySession(sessionId: Long): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Update
    suspend fun updateSession(session: ChatSession)

    @Query("DELETE FROM chat_message WHERE session_id = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: Long)

    @Query("DELETE FROM chat_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)
}
