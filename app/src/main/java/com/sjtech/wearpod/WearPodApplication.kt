package com.sjtech.wearpod

import android.app.Application
import com.sjtech.wearpod.core.AppContainer

class WearPodApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
