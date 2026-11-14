package com.aibuilt.pocketmasterai.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "chat_message",
    foreignKeys = [ForeignKey(
        entity = ChatSession::class,
        childColumns = ["session_id"],
        parentColumns = ["id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index(value = ["session_id"])]
)
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val session_id: Long,
    val role: String,
    val content: String,
    val created_at: Long = System.currentTimeMillis()
)
