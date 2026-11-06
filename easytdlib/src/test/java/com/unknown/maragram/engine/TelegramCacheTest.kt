package com.unknown.maragram.engine

import org.drinkless.tdlib.TdApi
import org.junit.Assert.*
import org.junit.Test

class TelegramCacheTest {
    private fun message(id: Long, chatId: Long = 7) = TdApi.Message().apply {
        this.id = id
        this.chatId = chatId
        content = TdApi.MessageText(TdApi.FormattedText("original", null), null, null)
    }

    private fun chat(id: Long, order: Long) = TdApi.Chat().apply {
        this.id = id
        title = "original"
        positions = arrayOf(TdApi.ChatPosition(TdApi.ChatListMain(), order, false, null))
    }

    @Test fun historyMergesWithLiveMessagesAndPaginationWithoutDuplicates() {
        val cache = TelegramCache()
        cache.onResult(TdApi.UpdateNewMessage(message(30)))
        cache.mergeHistory(7, listOf(message(20), message(30)))
        cache.mergeHistory(7, listOf(message(10), message(20)))
        assertEquals(listOf(10L, 20L, 30L), cache.messages.value[7]?.map { it.id })
    }

    @Test fun successfulAndFailedSendsReplaceTemporaryMessages() {
        val cache = TelegramCache()
        cache.onResult(TdApi.UpdateNewMessage(message(100)))
        cache.onResult(TdApi.UpdateMessageSendSucceeded().apply {
            oldMessageId = 100
            message = message(20)
        })
        assertEquals(listOf(20L), cache.messages.value[7]?.map { it.id })
        cache.onResult(TdApi.UpdateMessageSendFailed().apply {
            oldMessageId = 20
            message = message(25).apply { sendingState = TdApi.MessageSendingStateFailed() }
        })
        assertEquals(25L, cache.messages.value[7]?.single()?.id)
        assertTrue(cache.messages.value[7]?.single()?.sendingState is TdApi.MessageSendingStateFailed)
    }

    @Test fun editsPublishNewSnapshotsAndDeletionRemovesMessages() {
        val cache = TelegramCache()
        val original = message(10)
        cache.mergeHistory(7, listOf(original))
        val content = TdApi.MessageText().apply { text = TdApi.FormattedText("edited", null) }
        cache.onResult(TdApi.UpdateMessageContent(7, 10, content))
        assertNotSame(original, cache.messages.value[7]?.single())
        assertSame(content, cache.messages.value[7]?.single()?.content)
        assertNotSame(content, original.content)
        cache.onResult(TdApi.UpdateMessageEdited().apply {
            chatId = 7; messageId = 10; editDate = 42
        })
        assertEquals(42, cache.messages.value[7]?.single()?.editDate)
        cache.onResult(TdApi.UpdateDeleteMessages().apply {
            chatId = 7; messageIds = longArrayOf(10)
        })
        assertTrue(cache.messages.value[7].orEmpty().isEmpty())
    }

    @Test fun chatUpdatesPublishSnapshotsAndMainListTracksOrderAndRemoval() {
        val cache = TelegramCache()
        val original = chat(1, 10)
        cache.onResult(TdApi.UpdateNewChat(original))
        cache.onResult(TdApi.UpdateNewChat(chat(2, 20)))
        assertEquals(listOf(2L, 1L), cache.chatIds.value)
        cache.onResult(TdApi.UpdateChatTitle(1, "renamed"))
        assertEquals("renamed", cache.chats.value[1]?.title)
        assertEquals("original", original.title)
        cache.onResult(TdApi.UpdateChatReadInbox(1, 15, 3))
        assertEquals(3, cache.chats.value[1]?.unreadCount)
        cache.onResult(TdApi.UpdateChatPosition(1, TdApi.ChatPosition(TdApi.ChatListMain(), 30, false, null)))
        assertEquals(listOf(1L, 2L), cache.chatIds.value)
        cache.onResult(TdApi.UpdateChatPosition(1, TdApi.ChatPosition(TdApi.ChatListArchive(), 0, false, null)))
        assertEquals(listOf(1L, 2L), cache.chatIds.value)
        cache.onResult(TdApi.UpdateChatPosition(1, TdApi.ChatPosition(TdApi.ChatListMain(), 0, false, null)))
        assertEquals(listOf(2L), cache.chatIds.value)
    }

    @Test fun clearingSessionDropsAllAccountDataIncludingFileIds() {
        val cache = TelegramCache()
        cache.onResult(TdApi.UpdateNewChat(chat(1, 10)))
        cache.mergeHistory(7, listOf(message(10)))
        cache.setMe(TdApi.User().apply { id = 123 })
        cache.onResult(TdApi.UpdateFile(TdApi.File().apply {
            id = 1
            local = TdApi.LocalFile().apply {
                isDownloadingCompleted = true; path = "/previous-account/photo"
            }
        }))
        assertFalse(cache.downloadedFiles.value.isEmpty())
        cache.clear()
        assertTrue(cache.chats.value.isEmpty())
        assertTrue(cache.chatIds.value.isEmpty())
        assertTrue(cache.users.value.isEmpty())
        assertTrue(cache.messages.value.isEmpty())
        assertTrue(cache.downloadedFiles.value.isEmpty())
        assertNull(cache.me.value)
    }

    @Test fun lastMessageAndPhotoUpdatesPreservePreviousChatSnapshots() {
        val cache = TelegramCache()
        val original = chat(1, 10)
        cache.onResult(TdApi.UpdateNewChat(original))
        val lastMessage = message(50, 1)
        cache.onResult(TdApi.UpdateChatLastMessage(1, lastMessage, emptyArray()))
        assertSame(lastMessage, cache.chats.value[1]?.lastMessage)
        assertNull(original.lastMessage)
        assertTrue(cache.chatIds.value.isEmpty())
        val photo = TdApi.ChatPhotoInfo()
        cache.onResult(TdApi.UpdateChatPhoto(1, photo))
        assertSame(photo, cache.chats.value[1]?.photo)
        assertNull(original.photo)
    }
}
