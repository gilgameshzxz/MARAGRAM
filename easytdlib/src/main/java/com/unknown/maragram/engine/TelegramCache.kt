package com.unknown.maragram.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi

// Access is serialized by TelegramManager; snapshots must not mutate objects already emitted.
internal class TelegramCache {
    private val _chatIds = MutableStateFlow<List<Long>>(emptyList())
    val chatIds: StateFlow<List<Long>> = _chatIds.asStateFlow()

    private val _chats = MutableStateFlow<Map<Long, TdApi.Chat>>(emptyMap())
    val chats: StateFlow<Map<Long, TdApi.Chat>> = _chats.asStateFlow()

    private val _users = MutableStateFlow<Map<Long, TdApi.User>>(emptyMap())
    val users: StateFlow<Map<Long, TdApi.User>> = _users.asStateFlow()

    private val _me = MutableStateFlow<TdApi.User?>(null)
    val me: StateFlow<TdApi.User?> = _me.asStateFlow()

    private val _downloadedFiles = MutableStateFlow<Map<Int, String>>(emptyMap())
    val downloadedFiles: StateFlow<Map<Int, String>> = _downloadedFiles.asStateFlow()

    private val _messages = MutableStateFlow<Map<Long, List<TdApi.Message>>>(emptyMap())
    val messages: StateFlow<Map<Long, List<TdApi.Message>>> = _messages.asStateFlow()

    fun onResult(result: TdApi.Object) {
        when (result.constructor) {

            TdApi.UpdateNewChat.CONSTRUCTOR -> {
                val chat = (result as TdApi.UpdateNewChat).chat
                _chats.value = _chats.value + (chat.id to chat)
            }

            TdApi.UpdateUser.CONSTRUCTOR -> {
                val user = (result as TdApi.UpdateUser).user
                _users.value = _users.value + (user.id to user)
                if (user.id == _me.value?.id) {
                    _me.value = user
                }
            }

            TdApi.UpdateUserStatus.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateUserStatus
                val user = _users.value[update.userId]?.let(::copyUser) ?: return
                user.status = update.status
                _users.value = _users.value + (user.id to user)
            }

            TdApi.UpdateFile.CONSTRUCTOR -> {
                val file = (result as TdApi.UpdateFile).file
                if (file.local.isDownloadingCompleted) {
                    _downloadedFiles.value = _downloadedFiles.value + (file.id to file.local.path)
                }
            }

            TdApi.UpdateNewMessage.CONSTRUCTOR -> {
                val message = (result as TdApi.UpdateNewMessage).message
                _messages.update { currentMap ->
                    val currentList = currentMap[message.chatId] ?: emptyList()
                    if (currentList.none { it.id == message.id }) {
                        currentMap + (message.chatId to (currentList + message).sortedBy { it.id })
                    } else currentMap
                }
            }

            TdApi.UpdateMessageSendSucceeded.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateMessageSendSucceeded
                _messages.update { currentMap ->
                    val currentList = currentMap[update.message.chatId] ?: return@update currentMap
                    val newList = currentList.map {
                        if (it.id == update.oldMessageId) update.message else it
                    }.distinctBy { it.id }.sortedBy { it.id }
                    currentMap + (update.message.chatId to newList)
                }
            }

            TdApi.UpdateMessageSendFailed.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateMessageSendFailed
                _messages.update { currentMap ->
                    val currentList = currentMap[update.message.chatId] ?: return@update currentMap
                    val newList = currentList.map {
                        if (it.id == update.oldMessageId) update.message else it
                    }.distinctBy { it.id }.sortedBy { it.id }
                    currentMap + (update.message.chatId to newList)
                }
            }

            TdApi.UpdateMessageContent.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateMessageContent
                _messages.update { currentMap ->
                    val currentList = currentMap[update.chatId] ?: return@update currentMap
                    val newList = currentList.map {
                        if (it.id == update.messageId) {
                            return@map copyMessage(it).apply { content = update.newContent }
                        }
                        it
                    }
                    currentMap + (update.chatId to newList)
                }
            }

            TdApi.UpdateMessageEdited.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateMessageEdited
                _messages.update { currentMap ->
                    val currentList = currentMap[update.chatId] ?: return@update currentMap
                    val newList = currentList.map {
                        if (it.id == update.messageId) {
                            return@map copyMessage(it).apply {
                                editDate = update.editDate
                                replyMarkup = update.replyMarkup
                            }
                        }
                        it
                    }
                    currentMap + (update.chatId to newList)
                }
            }

            TdApi.UpdateMessageInteractionInfo.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateMessageInteractionInfo
                _messages.update { currentMap ->
                    val currentList = currentMap[update.chatId] ?: return@update currentMap
                    val newList = currentList.map {
                        if (it.id == update.messageId) {
                            copyMessage(it).apply { interactionInfo = update.interactionInfo }
                        } else it
                    }
                    currentMap + (update.chatId to newList)
                }
            }

            TdApi.UpdateDeleteMessages.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateDeleteMessages
                _messages.update { currentMap ->
                    val currentList = currentMap[update.chatId] ?: return@update currentMap
                    val deletedIds = update.messageIds.toSet()
                    val newList = currentList.filter { it.id !in deletedIds }
                    currentMap + (update.chatId to newList)
                }
            }

            TdApi.UpdateChatTitle.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateChatTitle
                val chat = _chats.value[update.chatId]?.let(::copyChat) ?: return
                chat.title = update.title
                _chats.value = _chats.value + (update.chatId to chat)
            }

            TdApi.UpdateChatLastMessage.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateChatLastMessage
                val chat = _chats.value[update.chatId]?.let(::copyChat) ?: return
                chat.lastMessage = update.lastMessage
                chat.positions = update.positions
                _chats.value = _chats.value + (update.chatId to chat)
            }

            TdApi.UpdateChatPhoto.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateChatPhoto
                val chat = _chats.value[update.chatId]?.let(::copyChat) ?: return
                chat.photo = update.photo
                _chats.value = _chats.value + (update.chatId to chat)
            }

            TdApi.UpdateChatReadInbox.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateChatReadInbox
                val chat = _chats.value[update.chatId]?.let(::copyChat) ?: return
                chat.unreadCount = update.unreadCount
                chat.lastReadInboxMessageId = update.lastReadInboxMessageId
                _chats.value = _chats.value + (update.chatId to chat)
            }

            TdApi.UpdateChatPosition.CONSTRUCTOR -> {
                val update = result as TdApi.UpdateChatPosition
                val chat = _chats.value[update.chatId]?.let(::copyChat) ?: return
                chat.positions = (chat.positions.filterNot { sameList(it.list, update.position.list) } + update.position).toTypedArray()
                _chats.value = _chats.value + (chat.id to chat)
            }

            else -> Unit
        }
        if (result is TdApi.UpdateNewChat || result is TdApi.UpdateChatLastMessage ||
            result is TdApi.UpdateChatPosition) refreshChatIds()
    }


    fun clear() {
        _chatIds.value = emptyList()
        _chats.value = emptyMap()
        _users.value = emptyMap()
        _me.value = null
        _downloadedFiles.value = emptyMap()
        _messages.value = emptyMap()
    }

    fun setMe(user: TdApi.User) {
        _me.value = user
        _users.value = _users.value + (user.id to user)
    }

    fun mergeHistory(chatId: Long, history: List<TdApi.Message>) {
        _messages.update { current ->
            val merged = (current[chatId].orEmpty() + history).distinctBy { it.id }.sortedBy { it.id }
            current + (chatId to merged)
        }
    }

    private fun sameList(first: TdApi.ChatList, second: TdApi.ChatList): Boolean =
        first.constructor == second.constructor &&
            (first !is TdApi.ChatListFolder || second is TdApi.ChatListFolder && first.chatFolderId == second.chatFolderId)

    private fun refreshChatIds() {
        _chatIds.value = _chats.value.values
            .filter { chat -> chat.positions.any { it.list is TdApi.ChatListMain && it.order != 0L } }
            .sortedWith(compareByDescending<TdApi.Chat> { chat ->
                chat.positions.first { it.list is TdApi.ChatListMain }.order
            }.thenByDescending { it.id })
            .map { it.id }
    }

    private fun copyChat(source: TdApi.Chat) = TdApi.Chat(
        source.id,
        source.type,
        source.title,
        source.photo,
        source.accentColorId,
        source.backgroundCustomEmojiId,
        source.upgradedGiftColors,
        source.profileAccentColorId,
        source.profileBackgroundCustomEmojiId,
        source.permissions,
        source.lastMessage,
        source.positions,
        source.chatLists,
        source.messageSenderId,
        source.blockList,
        source.hasProtectedContent,
        source.isTranslatable,
        source.isMarkedAsUnread,
        source.viewAsTopics,
        source.hasScheduledMessages,
        source.canBeDeletedOnlyForSelf,
        source.canBeDeletedForAllUsers,
        source.canBeReported,
        source.defaultDisableNotification,
        source.unreadCount,
        source.lastReadInboxMessageId,
        source.lastReadOutboxMessageId,
        source.unreadMentionCount,
        source.unreadReactionCount,
        source.unreadPollVoteCount,
        source.notificationSettings,
        source.availableReactions,
        source.messageAutoDeleteTime,
        source.emojiStatus,
        source.background,
        source.theme,
        source.actionBar,
        source.businessBotManageBar,
        source.videoChat,
        source.pendingJoinRequests,
        source.replyMarkupMessageId,
        source.draftMessage,
        source.clientData
    )

    private fun copyMessage(source: TdApi.Message) = TdApi.Message(
        source.id,
        source.senderId,
        source.chatId,
        source.sendingState,
        source.schedulingState,
        source.isOutgoing,
        source.isPinned,
        source.isFromOffline,
        source.canBeSaved,
        source.hasTimestampedMedia,
        source.isChannelPost,
        source.isPaidStarSuggestedPost,
        source.isPaidTonSuggestedPost,
        source.containsUnreadMention,
        source.date,
        source.editDate,
        source.forwardInfo,
        source.importInfo,
        source.interactionInfo,
        source.unreadReactions,
        source.factCheck,
        source.suggestedPostInfo,
        source.replyTo,
        source.topicId,
        source.selfDestructType,
        source.selfDestructIn,
        source.autoDeleteIn,
        source.viaBotUserId,
        source.senderBusinessBotUserId,
        source.senderBoostCount,
        source.senderTag,
        source.paidMessageStarCount,
        source.authorSignature,
        source.mediaAlbumId,
        source.effectId,
        source.restrictionInfo,
        source.summaryLanguageCode,
        source.content,
        source.replyMarkup
    )

    private fun copyUser(source: TdApi.User) = TdApi.User(
        source.id,
        source.firstName,
        source.lastName,
        source.usernames,
        source.phoneNumber,
        source.status,
        source.profilePhoto,
        source.accentColorId,
        source.backgroundCustomEmojiId,
        source.upgradedGiftColors,
        source.profileAccentColorId,
        source.profileBackgroundCustomEmojiId,
        source.emojiStatus,
        source.isContact,
        source.isMutualContact,
        source.isCloseFriend,
        source.verificationStatus,
        source.isPremium,
        source.isSupport,
        source.restrictionInfo,
        source.activeStoryState,
        source.restrictsNewChats,
        source.paidMessageStarCount,
        source.haveAccess,
        source.type,
        source.languageCode,
        source.addedToAttachmentMenu
    )
}
