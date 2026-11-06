package com.unknown.maragram

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.unknown.maragram.core.AnalyticsManager
import com.unknown.maragram.core.EngineModule
import com.unknown.maragram.core.LanguageManager
import com.unknown.maragram.engine.TelegramManager
import com.unknown.maragram.ui.AuthRouter
import org.drinkless.tdlib.TdApi

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        AnalyticsManager.init(applicationContext)
        
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                AnalyticsManager.onAppBackgrounded()
            }
        })

        val telegramManager = EngineModule.getTelegramManager(applicationContext)
        
        LanguageManager.init(applicationContext)

        setContent {
            val monochromeColorScheme = darkColorScheme(
                primary = Color.White,
                onPrimary = Color.Black,
                secondary = Color(0xFF888888),
                onSecondary = Color.White,
                background = Color.Black,
                surface = Color(0xFF111111),
                onSurface = Color.White,
                onSurfaceVariant = Color(0xFF666666),
                outline = Color(0xFF333333)
            )

            MaterialTheme(
                colorScheme = monochromeColorScheme
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val authState by telegramManager.authState.collectAsState()
                    AuthRouter(authState = authState, manager = telegramManager)
                }
            }
        }
    }
}
