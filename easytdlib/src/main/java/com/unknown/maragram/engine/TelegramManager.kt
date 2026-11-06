package com.unknown.maragram.engine

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi
import java.io.File

class TelegramManager internal constructor(
    private val filesDir: File,
    private val createConnection: ((TdApi.Object) -> Unit) -> TelegramConnection
) {
    constructor(context: Context) : this(context.filesDir, ::createNativeConnection)

    private val cache = TelegramCache()
    val chatIds = cache.chatIds
    val chats = cache.chats
    val users = cache.users
    val me = cache.me
    val messages = cache.messages
    val downloadedFiles = cache.downloadedFiles

    private val _authState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authState = _authState.asStateFlow()
    private val _availableAccounts = MutableStateFlow(listOf("primary"))
    val availableAccounts = _availableAccounts.asStateFlow()
    var activeAccountName = "primary"
        private set

    private var client: TelegramConnection? = null
    private var generation = 0L
    private var closing = false
    private var isEnabled = false
    private val apiId = com.unknown.maragram.easytdlib.BuildConfig.TELEGRAM_API_ID
    private val apiHash = com.unknown.maragram.easytdlib.BuildConfig.TELEGRAM_API_HASH

    @Synchronized
    fun initClient() {
        isEnabled = true
        if (client != null) return
        val session = ++generation
        client = createConnection { result ->
            synchronized(this) {
                if (session == generation) {
                    val state = (result as? TdApi.UpdateAuthorizationState)?.authorizationState
                    if (state is TdApi.AuthorizationStateClosed) {
                        client = null
                        closing = false
                        cache.clear()
                        _authState.value = null
                        if (isEnabled) initClient()
                    } else if (!closing && isEnabled) {
                        if (state != null) onAuthorizationStateUpdated(state) else cache.onResult(result)
                    }
                }
            }
        }
    }

    @Synchronized
    fun stopClient() {
        isEnabled = false
        closeClient()
    }

    private fun closeClient() {
        cache.clear()
        _authState.value = null
        if (client != null && !closing) {
            closing = true
            client?.send(TdApi.Close()) {}
        }
    }

    // Both unsolicited updates and request callbacks are serialized here. Late replies from
    // a closing account cannot repopulate the next account's caches or send new requests.
    @Synchronized
    private fun send(request: TdApi.Function<*>, callback: (TdApi.Object) -> Unit = {}) {
        val current = client ?: return
        if (closing || !isEnabled) return
        val session = generation
        current.send(request) { result ->
            synchronized(this) {
                if (session == generation && !closing && isEnabled) callback(result)
            }
        }
    }

    private fun onAuthorizationStateUpdated(state: TdApi.AuthorizationState) {
        _authState.value = state
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> {
                // Keep an existing installation's original primary database usable.
                val legacy = File(filesDir, "tdlib")
                val database = if (activeAccountName == "primary" && legacy.exists() &&
                    !File(filesDir, "tdlib_primary").exists()) legacy
                    else File(filesDir, "tdlib_$activeAccountName")
                send(TdApi.SetTdlibParameters(
                    false, database.absolutePath, "", null, true, true, true, true,
                    apiId, apiHash, "en", "Android", "Unknown", "1.0"
                ))
            }
            is TdApi.AuthorizationStateReady -> {
                loadChats()
                getMe()
            }
            else -> Unit
        }
    }

    fun sendPhoneNumber(phoneNumber: String) = send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))
    fun sendVerificationCode(code: String) = send(TdApi.CheckAuthenticationCode(code))
    fun sendPassword(password: String) = send(TdApi.CheckAuthenticationPassword(password))
    fun sendBotToken(token: String) = send(TdApi.CheckAuthenticationBotToken(token))
    fun logout() = send(TdApi.LogOut())

    fun getMe() = send(TdApi.GetMe()) { result ->
        if (result is TdApi.User) cache.setMe(result)
    }

    private fun loadChats() {
        send(TdApi.LoadChats(TdApi.ChatListMain(), 100)) { result ->
            when (result) {
                is TdApi.Ok -> loadChats()
                is TdApi.Error -> if (result.code != 404) Log.e("TDLib", "Unable to load chats: ${result.code}")
                else -> Unit
            }
        }
    }

    fun loadMessages(chatId: Long, fromMessageId: Long = 0, limit: Int = 50,
                     callback: (TdApi.Messages?) -> Unit = {}) {
        send(TdApi.GetChatHistory(chatId, fromMessageId, 0, limit.coerceIn(1, 100), false)) { result ->
            if (result is TdApi.Messages) {
                cache.mergeHistory(chatId, result.messages.filterNotNull())
                callback(result)
            } else callback(null)
        }
    }

    fun sendMessage(chatId: Long, text: String) {
        val content = TdApi.InputMessageText(TdApi.FormattedText(text, null), null, true)
        send(TdApi.SendMessage(chatId, null, null, null, null, content)) { result ->
            if (result is TdApi.Message) cache.mergeHistory(chatId, listOf(result))
        }
    }

    fun downloadFile(fileId: Int, priority: Int = 1) {
        send(TdApi.DownloadFile(fileId, priority.coerceIn(1, 32), 0, 0, false)) { result ->
            if (result is TdApi.File) cache.onResult(TdApi.UpdateFile(result))
        }
    }

    fun searchPublicChat(username: String, callback: (TdApi.Chat?) -> Unit) {
        send(TdApi.SearchPublicChat(username.removePrefix("@"))) { result ->
            if (result is TdApi.Chat) {
                cache.onResult(TdApi.UpdateNewChat(result))
                callback(result)
            } else callback(null)
        }
    }

    @Synchronized
    fun switchAccount(accountName: String) {
        require(accountName.matches(Regex("[A-Za-z0-9_-]+")))
        if (activeAccountName == accountName) return
        activeAccountName = accountName
        if (accountName !in _availableAccounts.value) {
            _availableAccounts.value = _availableAccounts.value + accountName
        }
        closeClient()
        if (client == null && isEnabled) initClient()
    }

    fun addNewAccountSession(): String {
        val name = "account_${System.currentTimeMillis()}"
        switchAccount(name)
        return name
    }

    fun getConstructor(obj: TdApi.Object?): Int = obj?.constructor ?: 0
}
