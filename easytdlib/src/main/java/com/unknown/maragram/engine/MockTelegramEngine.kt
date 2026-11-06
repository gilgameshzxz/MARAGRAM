package com.unknown.maragram.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A lightweight Mock Implementation of the Telegram Engine.
 * COMPLETELY DETACHED FROM TDLib.
 * Use this to deploy the application instantly during UI development and Ritual refining.
 */
class MockTelegramEngine : TelegramEngine {
    private val _authState = MutableStateFlow(SystemAuthState.READY)
    override val authState = _authState.asStateFlow()

    private val _chats = MutableStateFlow(listOf(
        SystemChat(1, "The Absolute", SystemMessage(101, 1, "Welcome to the ritual.", false, "System")),
        SystemChat(2, "Maragram Support", SystemMessage(102, 2, "Decoupled logic is faster.", false, "Support")),
        SystemChat(3, "Philosophical Inquiries", SystemMessage(103, 3, "Does the backend exist?", false, "Observer"))
    ))
    override val chats = _chats.asStateFlow()

    private val _messages = MutableStateFlow<Map<Long, List<SystemMessage>>>(emptyMap())
    override val messages = _messages.asStateFlow()

    override fun initClient() {}
    override fun sendPhoneNumber(phoneNumber: String) { _authState.value = SystemAuthState.WAITING_CODE }
    override fun sendVerificationCode(code: String) { _authState.value = SystemAuthState.READY }
    override fun sendPassword(password: String) { _authState.value = SystemAuthState.READY }
    override fun logout() { _authState.value = SystemAuthState.LOGGED_OUT }
    override fun loadMessages(chatId: Long) {}
    override fun sendMessage(chatId: Long, text: String) {}
    
    override fun getConstructorId(state: SystemAuthState): Int = -1
}
