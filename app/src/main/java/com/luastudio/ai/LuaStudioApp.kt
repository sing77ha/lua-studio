package com.luastudio.ai

import android.app.Application
import com.luastudio.ai.data.storage.PreferencesManager

class LuaStudioApp : Application() {

    lateinit var preferencesManager: PreferencesManager
        private set

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(this)
    }
}
