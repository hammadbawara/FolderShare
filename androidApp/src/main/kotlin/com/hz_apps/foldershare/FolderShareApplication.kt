package com.hz_apps.foldershare

import android.app.Application
import com.hz_apps.foldershare.core.discovery.AndroidContextProvider
import com.hz_apps.foldershare.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class FolderShareApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AndroidContextProvider.applicationContext = applicationContext
        initKoin {
            androidLogger()
            androidContext(this@FolderShareApplication)
        }
    }
}
