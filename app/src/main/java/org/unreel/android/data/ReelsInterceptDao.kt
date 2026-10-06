package org.unreel.android.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReelsInterceptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIntercept(entity: ReelsInterceptEntity): Long

    @Query("SELECT COUNT(*) FROM reels_intercepts WHERE timestampEpochMs >= :startEpochMs")
    fun getCountSince(startEpochMs: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM reels_intercepts")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT * FROM reels_intercepts ORDER BY timestampEpochMs DESC")
    fun getAllIntercepts(): Flow<List<ReelsInterceptEntity>>
}
