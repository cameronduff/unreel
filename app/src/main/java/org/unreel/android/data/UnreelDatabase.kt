package org.unreel.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ReelsInterceptEntity::class], version = 1, exportSchema = false)
abstract class UnreelDatabase : RoomDatabase() {
    abstract fun reelsInterceptDao(): ReelsInterceptDao

    companion object {
        private const val DATABASE_NAME = "unreel_database.db"

        @Volatile
        private var instance: UnreelDatabase? = null

        fun getInstance(context: Context): UnreelDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    UnreelDatabase::class.java,
                    DATABASE_NAME
                ).build().also { instance = it }
            }
        }
    }
}
