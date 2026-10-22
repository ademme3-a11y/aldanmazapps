package com.aldfinans.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, IncomeEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AlDFinansDatabase : RoomDatabase() {
    abstract fun financialDao(): FinancialDao

    companion object {
        @Volatile private var INSTANCE: AlDFinansDatabase? = null

        fun get(context: Context): AlDFinansDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AlDFinansDatabase::class.java,
                    "ald_finans_v2.db"
                ).build().also { INSTANCE = it }
            }
    }
}
