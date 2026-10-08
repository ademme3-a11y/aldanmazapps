package com.aldfinans.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [TransactionEntity::class, IncomeEntity::class, CategoryEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AlDFinansDatabase : RoomDatabase() {
    abstract fun financialDao(): FinancialDao
}
