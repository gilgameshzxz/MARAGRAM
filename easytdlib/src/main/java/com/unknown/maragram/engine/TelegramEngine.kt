package com.unknown.maragram.engine

import kotlinx.coroutines.flow.StateFlow

/**
 * Authoritative Interface for the Telegram Backend.
 * Uses Domain Models (SystemChat, SystemMessage, etc.) to decouple the UI from TDLib.
 * This allows for faster deployments by removing the 4.8MB TdApi.java dependency during UI work.
 */
interface TelegramEngine {
    val authState: StateFlow<SystemAuthState>
    val chats: StateFlow<List<SystemChat>>
    val messages: StateFlow<Map<Long, List<SystemMessage>>>

    fun initClient()
    fun sendPhoneNumber(phoneNumber: String)
    fun sendVerificationCode(code: String)
    fun sendPassword(password: String)
    fun logout()
    fun loadMessages(chatId: Long)
    fun sendMessage(chatId: Long, text: String)
    
    // For legacy/bridge support if needed
    fun getConstructorId(state: SystemAuthState): Int
}
