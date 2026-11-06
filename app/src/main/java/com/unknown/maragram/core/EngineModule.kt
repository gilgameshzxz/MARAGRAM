package com.unknown.maragram.core

import android.content.Context
import com.unknown.maragram.engine.TelegramManager

object EngineModule {
    private var primaryManager: TelegramManager? = null

    @Synchronized
    fun getTelegramManager(context: Context): TelegramManager =
        primaryManager ?: TelegramManager(context.applicationContext).also {
            primaryManager = it
            it.initClient()
        }
}
