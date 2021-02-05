package com.enecuum.app.vvm.home

import android.content.Context
import com.enecuum.lib.api.main.ApiRouter
import com.enecuum.lib.api.main.Api
import com.enecuum.lib.api.Key
import com.enecuum.lib.KeyStore
import com.enecuum.app.data.livedata.DetailedBalanceLiveDataRepository
import com.enecuum.app.data.livedata.TokensBalanceLiveDataRepository
import com.enecuum.app.vvm.common.BalanceViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext



class HomeViewModel(
    context: Context,
    api: Api,
    detailedBalanceRepo: DetailedBalanceLiveDataRepository,
    tokensBalanceRepo: TokensBalanceLiveDataRepository
) : BalanceViewModel(context, api, detailedBalanceRepo, tokensBalanceRepo) {

    fun checkBalance(enabled: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val request = api.getDetailedBalanceAsync(ApiRouter.Route.DETAILED_BALANCE.url, KeyStore.publicKey(context))
            try {

                val response = request.await()
                enabled()

                withContext(Dispatchers.Main) {
//                    mutableStatistic.value = response
                }

            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun get25BIT(enabled: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            val key = KeyStore.publicKey(context)

            val request = api.get25BITAsync("https://faucet-bit.enecuum.com/", Key(key))
            try {

                val response = request.await()
                enabled()

                withContext(Dispatchers.Main) {

                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}