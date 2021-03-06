package com.radixdlt.android.data.model.transaction

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.reactivex.Flowable
import io.reactivex.Maybe

@Dao
interface TransactionsDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTransactions(transactionEntity: List<TransactionEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertTransaction(transactionEntity: TransactionEntity)

    @Query("SELECT * FROM TransactionEntity")
    fun getAllTransactions(): Maybe<MutableList<TransactionEntity>>

    @Query("SELECT * FROM TransactionEntity ORDER BY dateUnix DESC LIMIT 1")
    fun getLatestTransaction(): Flowable<TransactionEntity>

    @Query("SELECT * FROM TransactionEntity WHERE address = :address ORDER BY dateUnix DESC")
    fun getAllTransactionsByAddress(address: String): Maybe<MutableList<TransactionEntity>>

    @Query("DELETE FROM TransactionEntity")
    fun deleteTable()
}
