package com.kopilka.android

import android.app.Application
import androidx.work.Configuration
import com.kopilka.android.util.NotificationHelper

class KopilkaApp : Application(), Configuration.Provider {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper(this).createChannels()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()
}
