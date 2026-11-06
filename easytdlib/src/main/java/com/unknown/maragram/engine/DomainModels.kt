package com.unknown.maragram.engine

/**
 * Domain-specific models that are decoupled from TDLib.
 * These allows the UI to remain functional even when the heavy TDLib backend is detached.
 */

data class SystemChat(
    val id: Long,
    val title: String,
    val lastMessage: SystemMessage? = null,
    val unreadCount: Int = 0,
    val photoPath: String? = null
)

data class SystemMessage(
    val id: Long,
    val chatId: Long,
    val text: String,
    val isOutgoing: Boolean = false,
    val senderName: String? = null
)

data class SystemUser(
    val id: Long,
    val firstName: String,
    val lastName: String? = null,
    val username: String? = null
)

enum class SystemAuthState {
    UNKNOWN,
    LOGGED_OUT,
    WAITING_PHONE,
    WAITING_CODE,
    WAITING_PASSWORD,
    READY
}
