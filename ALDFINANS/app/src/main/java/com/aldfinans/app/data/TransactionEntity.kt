package com.aldfinans.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTime: Long,
    val bank: String,
    val amount: Double,
    val category: String,
    val merchant: String?,
    val cardLast4: String?,
    val source: String,
    val rawData: String?,
    val transactionType: String,
    val note: String? = null,
    val fingerprint: String? = null
)

@Entity(tableName = "incomes")
data class IncomeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateTime: Long,
    val amount: Double,
    val incomeType: String,
    val bank: String?,
    val description: String?,
    val note: String? = null
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val name: String,
    val parent: String? = null,
    val enabled: Boolean = true
)
