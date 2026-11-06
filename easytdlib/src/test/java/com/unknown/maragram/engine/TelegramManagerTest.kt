package com.unknown.maragram.engine

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TelegramManagerTest {
    private class Connection(val update: (TdApi.Object) -> Unit) : TelegramConnection {
        val requests = mutableListOf<Pair<TdApi.Function<*>, (TdApi.Object) -> Unit>>()
        override fun send(request: TdApi.Function<*>, callback: (TdApi.Object) -> Unit) {
            requests.add(request to callback)
        }
        fun state(state: TdApi.AuthorizationState) = update(TdApi.UpdateAuthorizationState(state))
    }

    private class Harness {
        val connections = mutableListOf<Connection>()
        val manager = TelegramManager(File("session-test")) { update ->
            Connection(update).also { connections.add(it) }
        }
    }

    @Test fun disablingWaitsForCloseAndDoesNotRestart() {
        val h = Harness()
        h.manager.initClient()
        h.manager.initClient()
        assertEquals(1, h.connections.size)
        val old = h.connections.single()
        h.manager.stopClient()
        assertTrue(old.requests.single().first is TdApi.Close)
        old.state(TdApi.AuthorizationStateClosed())
        assertEquals(1, h.connections.size)
        h.manager.initClient()
        assertEquals(2, h.connections.size)
    }

    @Test fun reenablingDuringCloseWaitsForOldDatabaseToClose() {
        val h = Harness()
        h.manager.initClient()
        val old = h.connections.single()
        h.manager.stopClient()
        h.manager.initClient()
        assertEquals(1, h.connections.size)
        old.state(TdApi.AuthorizationStateClosed())
        assertEquals(2, h.connections.size)
    }

    @Test fun switchingClearsCachesAndIgnoresLateRepliesAndUpdates() {
        val h = Harness()
        h.manager.initClient()
        val old = h.connections.single()
        val message = TdApi.Message().apply { id = 10; chatId = 7 }
        old.update(TdApi.UpdateNewMessage(message))
        h.manager.loadMessages(7)
        val reply = old.requests.last().second
        h.manager.switchAccount("second")
        assertTrue(h.manager.messages.value.isEmpty())
        old.update(TdApi.UpdateNewMessage(message))
        reply(TdApi.Messages().apply { messages = arrayOf(message) })
        assertTrue(h.manager.messages.value.isEmpty())
        old.state(TdApi.AuthorizationStateClosed())
        assertEquals(2, h.connections.size)
        old.update(TdApi.UpdateNewMessage(message))
        reply(TdApi.Messages().apply { messages = arrayOf(message) })
        old.state(TdApi.AuthorizationStateClosed())
        assertEquals(2, h.connections.size)
        assertTrue(h.manager.messages.value.isEmpty())
    }

    @Test fun readinessOnlyLoadsChatsAndIdentityWithoutSendingMessagesOrContacts() {
        val h = Harness()
        h.manager.initClient()
        val connection = h.connections.single()
        connection.state(TdApi.AuthorizationStateReady())
        assertEquals(listOf(TdApi.LoadChats::class.java, TdApi.GetMe::class.java),
            connection.requests.map { it.first.javaClass })
    }

    @Test fun historyReplyPopulatesObservedMessagesWithoutCallback() {
        val h = Harness()
        h.manager.initClient()
        h.manager.loadMessages(7)
        h.connections.single().requests.single().second(TdApi.Messages().apply {
            messages = arrayOf(TdApi.Message().apply { id = 10; chatId = 7 })
        })
        assertEquals(10L, h.manager.messages.value[7]?.single()?.id)
    }
}
