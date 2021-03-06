package com.radixdlt.android.data.model.transaction

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import kotlinx.android.parcel.Parcelize

@Parcelize
@Entity(
    primaryKeys = ["dateUnix"]
)
data class TransactionEntity(
    @ColumnInfo(index = true)
    val address: String,
    val subUnitAmount: Long,
    val formattedAmount: String,
    val message: String?,
    val sent: Boolean,
    val dateUnix: Long,
    val tokenClassISO: String,
    val tokenClassSubUnits: Int
) : Parcelable
