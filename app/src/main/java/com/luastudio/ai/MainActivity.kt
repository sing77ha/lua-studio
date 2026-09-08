package com.luastudio.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.luastudio.ai.domain.model.AppearanceSettings
import com.luastudio.ai.ui.navigation.LuaStudioNavGraph
import com.luastudio.ai.ui.theme.LuaStudioTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as LuaStudioApp

        setContent {
            val appearance by app.preferencesManager.appearanceSettings
                .collectAsState(initial = AppearanceSettings())

            LuaStudioTheme(
                themeMode = appearance.themeMode,
                accentColor = appearance.accentColor
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LuaStudioNavGraph(preferencesManager = app.preferencesManager)
                }
            }
        }
    }
}
