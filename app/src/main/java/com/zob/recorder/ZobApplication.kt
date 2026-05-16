package com.zob.recorder

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ZobApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Notification channel creation will be added in T10
    }
}
