package org.unreel.android.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ReelsInterceptDaoTest {

    private lateinit var database: UnreelDatabase
    private lateinit var dao: ReelsInterceptDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, UnreelDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.reelsInterceptDao()
    }

    @After
    fun closeDb() {
        database.close()
    }

    @Test
    fun testInsertAndCountSince() = runBlocking {
        val baseTime = 10_000L

        // Insert 2 records before baseTime, and 3 records after baseTime
        dao.insertIntercept(ReelsInterceptEntity(timestampEpochMs = baseTime - 5000))
        dao.insertIntercept(ReelsInterceptEntity(timestampEpochMs = baseTime - 1000))
        dao.insertIntercept(ReelsInterceptEntity(timestampEpochMs = baseTime))
        dao.insertIntercept(ReelsInterceptEntity(timestampEpochMs = baseTime + 2000))
        dao.insertIntercept(ReelsInterceptEntity(timestampEpochMs = baseTime + 5000))

        val countSince = dao.getCountSince(baseTime).first()
        assertEquals(3, countSince)

        val totalCount = dao.getTotalCount().first()
        assertEquals(5, totalCount)
    }

    @Test
    fun testFlowEmitsUpdatesOnInsert() = runBlocking {
        assertEquals(0, dao.getTotalCount().first())

        val id = dao.insertIntercept(
            ReelsInterceptEntity(
                timestampEpochMs = 50_000L,
                triggerType = ReelsInterceptEntity.TRIGGER_BOTTOM_NAV_TAB
            )
        )
        assertTrue(id > 0)
        assertEquals(1, dao.getTotalCount().first())

        val list = dao.getAllIntercepts().first()
        assertEquals(1, list.size)
        assertEquals(ReelsInterceptEntity.TRIGGER_BOTTOM_NAV_TAB, list[0].triggerType)
    }
}
