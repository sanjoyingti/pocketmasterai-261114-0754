package com.aibuilt.pocketmasterai.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tone_snapshot")
data class ToneSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val snapshotData: String,
    val created_at: Long = System.currentTimeMillis()
)
