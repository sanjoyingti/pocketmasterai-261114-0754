package com.aibuilt.pocketmasterai.data.local.dao

import androidx.room.*
import com.aibuilt.pocketmasterai.data.local.ToneSnapshot
import kotlinx.coroutines.flow.Flow

@Dao
interface ToneSnapshotDao {
    @Query("SELECT * FROM tone_snapshot ORDER BY created_at DESC LIMIT 1")
    fun getLatestSnapshot(): Flow<ToneSnapshot?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: ToneSnapshot): Long

    @Query("DELETE FROM tone_snapshot")
    suspend fun clearSnapshots()
}
