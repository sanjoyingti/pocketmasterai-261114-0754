package com.aibuilt.pocketmasterai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aibuilt.pocketmasterai.data.local.dao.ChatDao
import com.aibuilt.pocketmasterai.data.local.dao.ToneSnapshotDao

@Database(
    entities = [ChatSession::class, ChatMessage::class, ToneSnapshot::class],
    version = 1,
    exportSchema = false
)
abstract class PocketMasterDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun toneSnapshotDao(): ToneSnapshotDao
}
