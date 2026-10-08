package com.aldfinans.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancialDao {
    @Query("SELECT * FROM transactions ORDER BY dateTime DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Insert
    suspend fun insertTransaction(item: TransactionEntity): Long

    @Insert
    suspend fun insertIncome(item: IncomeEntity): Long

    @Query("SELECT * FROM incomes ORDER BY dateTime DESC")
    fun observeIncomes(): Flow<List<IncomeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(item: CategoryEntity)

    @Query("SELECT * FROM categories WHERE enabled = 1 ORDER BY name")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT COUNT(*) FROM transactions WHERE fingerprint = :fingerprint")
    suspend fun countByFingerprint(fingerprint: String): Int
}
