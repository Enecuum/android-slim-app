package com.enecuum.app.di

import com.enecuum.app.data.livedata.*
import com.enecuum.app.vvm.common.BalanceViewModel
import com.enecuum.app.vvm.home.HomeViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.android.viewmodel.ext.koin.viewModel
import org.koin.dsl.module.module

val appModule = module {

    single<DetailedBalanceLiveDataRepository> { DetailedBalanceLiveData() }
    single<TokensBalanceLiveDataRepository> { TokensBalanceLiveData() }
    single<TransactionLiveDataRepository> { TransactionLiveData() }
    single<StatisticLiveDataRepository> { StatisticLiveData() }
    single<RoiLiveDataRepository> { RoiLiveData() }
    single<TickersLiveDataRepository> { TickersLiveData() }
    single<StakeProvidersLiveDataRepository> { StakeProvidersLiveData() }
    single<ValidatorsLiveDataRepository> { ValidatorsLiveData() }
    single<PosNamesLiveDataRepository> { PosNamesLiveData() }

    viewModel { HomeViewModel(androidContext(), get(), get(), get()) }
    viewModel { BalanceViewModel(androidContext(), get(), get(), get()) }
}