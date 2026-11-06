package com.unknown.maragram.engine

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

internal interface TelegramConnection {
    fun send(request: TdApi.Function<*>, callback: (TdApi.Object) -> Unit)
}

internal fun createNativeConnection(onUpdate: (TdApi.Object) -> Unit): TelegramConnection {
    System.loadLibrary("tdjni")
    Client.execute(TdApi.SetLogVerbosityLevel(0))
    val client = Client.create({ onUpdate(it) }, null, null)
    return object : TelegramConnection {
        override fun send(request: TdApi.Function<*>, callback: (TdApi.Object) -> Unit) {
            client.send(request) { callback(it) }
        }
    }
}
