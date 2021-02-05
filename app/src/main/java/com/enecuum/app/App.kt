package com.enecuum.app

import android.content.Context
import android.content.ContextWrapper
import androidx.multidex.MultiDex
import androidx.multidex.MultiDexApplication
import com.enecuum.app.di.networkModule
import com.enecuum.app.di.appModule
import com.enecuum.lib.api.main.Api
import com.enecuum.lib.api.main.ApiRouter
import com.pixplicity.easyprefs.library.Prefs
import org.koin.android.ext.android.startKoin

class App : MultiDexApplication() {

    override fun onCreate() {
        super.onCreate()
        startKoin(this, listOf(appModule, networkModule))

        val setter = ApiRouter.getConnectionSetter(true); // We use bit network here
        ApiRouter.setter = setter

        Prefs.Builder()
            .setContext(this)
            .setMode(ContextWrapper.MODE_PRIVATE)
            .setPrefsName(packageName)
            .setUseDefaultSharedPreference(true)
            .build()
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        MultiDex.install(this)
    }
}