package com.radixdlt.android.data.model.transaction

import android.content.Context
import androidx.lifecycle.MediatorLiveData
import com.radixdlt.android.data.mapper.TokenTransferDataMapper
import com.radixdlt.android.identity.Identity
import com.radixdlt.android.util.FAUCET_ADDRESS
import com.radixdlt.android.util.QueryPreferences
import com.radixdlt.client.application.objects.TokenTransfer
import com.radixdlt.client.core.address.RadixAddress
import com.radixdlt.client.dapps.messaging.RadixMessaging
import com.radixdlt.client.dapps.wallet.RadixWallet
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.Observables
import io.reactivex.rxkotlin.addTo
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import java.util.concurrent.TimeUnit

class TransactionsRepository(
    val context: Context,
    private val transactionsDao: TransactionsDao
) : MediatorLiveData<MutableList<TransactionEntity>>() {

    private val compositeDisposable = CompositeDisposable()
    private val myAddress: String by lazy { QueryPreferences.getPrefAddress(context) }

    override fun onActive() {
        super.onActive()
        retrieveAllTransactions()
    }

    private fun retrieveAllTransactions() {
        val radixWalletTransactionsObservable = RadixWallet(Identity.api!!)
            .getTransactions(RadixAddress.fromString(myAddress))

        val allTransactions: Observable<TokenTransfer> = radixWalletTransactionsObservable
            .publish()
            .refCount(2)

        val oldTransactionsList = retrieveListOfOldTransactions(allTransactions)

        listenToNewTransactions(oldTransactionsList, allTransactions)
    }

    private fun listenToNewTransactions(
        oldTransactionsList: Single<ArrayList<TokenTransfer>>,
        allTransactions: Observable<TokenTransfer>
    ) {
        Observables.combineLatest(
            oldTransactionsList.toObservable(),
            allTransactions
        ) { listOfOld, transaction ->
            if (listOfOld.contains(transaction)) {
                Maybe.empty()
            } else {
                Maybe.just(transaction)
            }
        }.flatMapMaybe { it }
            .map { mutableListOf(TokenTransferDataMapper.transform(it, myAddress)) }
            .subscribeOn(Schedulers.io())
            .subscribe({
                transactionsDao.insertTransaction(it.first()) // insert in DB
                postValue(it)
            }, Throwable::printStackTrace)
            .addTo(compositeDisposable)
    }

    private fun retrieveListOfOldTransactions(
        allTransactions: Observable<TokenTransfer>
    ): Single<ArrayList<TokenTransfer>> {

        getStoredTransactions()

        val oldTransactionsList: Single<ArrayList<TokenTransfer>> = allTransactions
            .scan(ArrayList<TokenTransfer>()) { list, transaction ->
                list.add(transaction)
                return@scan list
            }
            .debounce(3, TimeUnit.SECONDS)
            .firstOrError()
            .cache()

        oldTransactionsList
            .toObservable()
            .flatMapIterable { list -> list }
            .map { TokenTransferDataMapper.transform(it, myAddress) }
            .toList()
            .subscribeOn(Schedulers.io())
            .subscribe({
                transactionsDao.insertTransactions(it) // insert in DB
                getStoredTransactions()
            }, Throwable::printStackTrace)
            .addTo(compositeDisposable)

        return oldTransactionsList
    }

    private fun getStoredTransactions() {
        transactionsDao.getAllTransactions()
            .subscribeOn(Schedulers.io())
            .subscribe {
                Timber.tag("TransactionsLooking").d(it.toString())
                postValue(it)
            }
            .addTo(compositeDisposable)
    }

    /**
     * Temporary method toAddress request tokens from faucet which gets added to the common
     * composite disposable which gets disposed when inactive.
     * */
    fun requestTestTokenFromFaucet() {
        // Send a message!
        RadixMessaging(Identity.api!!).sendMessage(
            "Give me some radix!!",
            RadixAddress.fromString(FAUCET_ADDRESS)
        )
            .toCompletable()
            .subscribeOn(Schedulers.io())
            .subscribe {
                Timber.d("Submitted")
            }.addTo(compositeDisposable)
    }

    override fun onInactive() {
        super.onInactive()
        Timber.d("onInactive")
        compositeDisposable.clear()
    }
}
