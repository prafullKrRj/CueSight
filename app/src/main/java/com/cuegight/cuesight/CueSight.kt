package com.cuegight.cuesight

import android.app.Application
import com.cuegight.cuesight.di.appModule
import com.cuegight.cuesight.feature.practice.di.practiceModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class CueSight : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@CueSight)
            modules(appModule, practiceModule)
        }
    }
}
