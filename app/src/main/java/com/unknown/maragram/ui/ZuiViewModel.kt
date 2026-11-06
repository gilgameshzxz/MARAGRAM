package com.unknown.maragram.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import com.unknown.maragram.core.AnalyticsManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.unknown.maragram.engine.TelegramManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject

class ChatNodeState(
    val id: Long,
    initialPosition: Offset = Offset.Zero
) {
    var position by mutableStateOf(initialPosition)
}

class ChatWindowState(
    val chatId: Long,
    initialPosition: Offset = Offset.Zero,
    initialWidth: Float = 500f,
    initialHeight: Float = 700f
) {
    var worldPosition by mutableStateOf(initialPosition)
    var width by mutableStateOf(initialWidth)
    var height by mutableStateOf(initialHeight)
    var isFullScreen by mutableStateOf(false)
    
    // Internal spatial state for Chat Space
    var localCameraOffset by mutableStateOf(Offset.Zero)
    var localCameraZoom by mutableStateOf(1f)
}

class MessageNodeState(
    val id: Long,
    val chatId: Long,
    initialPosition: Offset = Offset.Zero
) {
    var position by mutableStateOf(initialPosition)
    var zIndex by mutableStateOf(0f)
    
    val roundedPosition: IntOffset
        get() = IntOffset(position.x.toInt(), position.y.toInt())
}

class ZuiViewModel(private val context: Context, val manager: TelegramManager) : ViewModel() {

    private val prefs = context.getSharedPreferences("zui_spatial_state", Context.MODE_PRIVATE)

    var cameraOffset by mutableStateOf(Offset.Zero)
    var cameraZoom by mutableStateOf(1f)

    var selectedChatId by mutableStateOf<Long?>(null)
    val openWindows = mutableStateListOf<ChatWindowState>()
    val nodes = mutableStateMapOf<Long, ChatNodeState>()

    // Telegram Visibility States
    val visibleChatIds = mutableStateListOf<Long>()
    var isFirstTelegramActivation by mutableStateOf(true)

    // Permanent System Nodes
    var settingsNodePos by mutableStateOf(Offset(50f, 50f))

    var wallpaperUri by mutableStateOf<Uri?>(null)
    var windowOpacity by mutableStateOf(0.85f)
    var messageOpacity by mutableStateOf(1.0f)
    var searchQuery by mutableStateOf("")
    var focusMode by mutableStateOf(false)
    var zAxisEnabled by mutableStateOf(false)
    var renderPercentage by mutableStateOf(0.01f)
    var isSettingsVisible by mutableStateOf(false)
    var isAboutVisible by mutableStateOf(false)
    var isWorkspaceSettingsVisible by mutableStateOf(false)
    var isPluginsSettingsVisible by mutableStateOf(false)
    var isBehaviorSettingsVisible by mutableStateOf(false)
    var isTelegramAccountSettingsVisible by mutableStateOf(false)
    var isTelegramVisibilitySettingsVisible by mutableStateOf(false)
    var isDynamicTextScalingEnabled by mutableStateOf(false)
    var isSpatialChatEnabled by mutableStateOf(false)
    var isSpatialChatDetailEnabled by mutableStateOf(false)
    var isOffScreenIndicatorEnabled by mutableStateOf(true)
    var isStaticButtonsEnabled by mutableStateOf(false)

    var noRenderingConstraints by mutableStateOf(false)

    var isInputBarForcedVisible by mutableStateOf(false)

    var isReady by mutableStateOf(false)

    var focusedChatId by mutableStateOf<Long?>(null)
    var messageDetailScale by mutableStateOf(1f)
    var messageDetailOffset by mutableStateOf(Offset.Zero)

    val messageNodes = mutableStateMapOf<Long, SnapshotStateMap<Long, MessageNodeState>>()
    val selectedMessageIds = mutableStateMapOf<Long, SnapshotStateList<Long>>()

    fun getMessageNode(chatId: Long, messageId: Long): MessageNodeState {
        val chatMap = messageNodes.getOrPut(chatId) { mutableStateMapOf() }
        val node = chatMap[messageId]
        if (node != null) return node
        
        // Fallback for immediate UI needs
        return MessageNodeState(messageId, chatId, Offset(0f, chatMap.size * 250f))
    }

    init {
        viewModelScope.launch(Dispatchers.Default) {
            loadSpatialState()
            
            // Critical: Ensure isReady is true immediately after state is loaded
            // This prevents sticking on the "System Initializing" screen
            withContext(Dispatchers.Main) {
                isReady = true
            }
            
            launch {
                manager.chatIds.collectLatest { ids ->
                    updateSpatialModel(ids)
                }
            }
            
            launch {
                manager.me.collect { user ->
                    if (user != null && isFirstTelegramActivation) {
                        if (!visibleChatIds.contains(user.id)) {
                            visibleChatIds.add(user.id)
                            isFirstTelegramActivation = false
                            updateSpatialModel(manager.chatIds.value)
                            saveSpatialState()
                        }
                    }
                }
            }

            // Background message node population to prevent UI thrashing and nesting
            launch {
                manager.messages.collectLatest { messagesMap ->
                    for ((chatId, msgs) in messagesMap) {
                        val chatMap = messageNodes.getOrPut(chatId) { mutableStateMapOf() }
                        if (msgs.isEmpty()) continue

                        // Find an anchor: the first message in the list that already has a position
                        var anchorIndex = -1
                        var anchorY = 0f

                        for (i in msgs.indices) {
                            val existing = chatMap[msgs[i].id]
                            if (existing != null) {
                                anchorIndex = i
                                anchorY = existing.position.y
                                break
                            }
                        }

                        // If no messages have positions yet, start from the first one at Y=0
                        if (anchorIndex == -1) {
                            anchorIndex = 0
                            anchorY = 0f
                        }

                        // Position all messages relative to the anchor
                        for (i in msgs.indices) {
                            val msg = msgs[i]
                            if (!chatMap.containsKey(msg.id)) {
                                val relativeY = anchorY + (i - anchorIndex) * 250f
                                val newNode = MessageNodeState(msg.id, chatId, Offset(0f, relativeY))
                                chatMap[msg.id] = newNode
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateSpatialModel(ids: List<Long>) {
        val filteredIds = ids.filter { visibleChatIds.contains(it) }
        val currentKeys = nodes.keys.toSet()
        val newKeys = filteredIds.toSet()
        (currentKeys - newKeys).forEach { nodes.remove(it) }

        if (isSpatialChatEnabled) {
            val columns = 8
            val cellWidth = 400f 
            val cellHeight = 150f

            filteredIds.forEachIndexed { index, id ->
                if (!nodes.containsKey(id)) {
                    val col = index % columns
                    val row = index / columns
                    val x = col * cellWidth
                    val y = row * cellHeight
                    nodes[id] = ChatNodeState(id, Offset(x, y))
                }
            }
        } else {
            val cellWidth = 350f
            val cellHeight = 100f
            val maxRowsPerColumn = 10

            filteredIds.forEachIndexed { index, id ->
                val col = index / maxRowsPerColumn
                val row = index % maxRowsPerColumn
                val x = col * cellWidth
                val y = row * cellHeight
                
                if (nodes.containsKey(id)) {
                    nodes[id]?.position = Offset(x, y)
                } else {
                    nodes[id] = ChatNodeState(id, Offset(x, y))
                }
            }
        }
    }

    fun onTransform(zoomDelta: Float, panDelta: Offset, centroid: Offset) {
        if (zoomDelta != 1f || panDelta != Offset.Zero) {
            AnalyticsManager.trackEvent("spatial_interactions")
        }
        val oldZoom = cameraZoom
        val newZoom = (cameraZoom * zoomDelta).coerceIn(0.01f, 20f)
        val worldPoint = (centroid / oldZoom) - cameraOffset
        cameraZoom = newZoom
        cameraOffset = (centroid / newZoom) - worldPoint
        cameraOffset += panDelta / cameraZoom
    }

    fun setWallpaper(uri: Uri?) {
        wallpaperUri = uri
        prefs.edit().putString("wallpaper_uri", uri?.toString()).apply()
    }

    fun onInteractionEnd() {
        saveSpatialState()
    }

    fun selectChat(id: Long) {
        selectedChatId = id
        AnalyticsManager.trackEvent("chats_opened")
        val existing = openWindows.find { it.chatId == id }
        if (existing == null) {
            val node = nodes[id]
            val nodePos = node?.position ?: Offset.Zero
            openWindows.add(ChatWindowState(chatId = id, initialPosition = nodePos + Offset(50f, -50f)))
        } else {
            focusWindow(id)
        }
        manager.loadMessages(id)
        saveSpatialState()
    }

    fun closeChat(id: Long) {
        openWindows.removeAll { it.chatId == id }
        if (selectedChatId == id) {
            selectedChatId = null
        }
        saveSpatialState()
    }

    fun focusWindow(chatId: Long) {
        val index = openWindows.indexOfFirst { it.chatId == chatId }
        if (index != -1 && index != openWindows.size - 1) {
            val window = openWindows.removeAt(index)
            openWindows.add(window)
        }
        manager.loadMessages(chatId)
    }

    fun moveWindow(chatId: Long, delta: Offset) {
        val window = openWindows.find { it.chatId == chatId }
        if (window != null) {
            window.worldPosition += delta / cameraZoom
            AnalyticsManager.trackEvent("spatial_interactions")
        }
    }

    fun resizeWindow(chatId: Long, delta: Offset) {
        val window = openWindows.find { it.chatId == chatId }
        if (window != null) {
            window.width = (window.width + delta.x / cameraZoom).coerceAtLeast(100f)
            window.height = (window.height + delta.y / cameraZoom).coerceAtLeast(150f)
            AnalyticsManager.trackEvent("spatial_interactions")
        }
    }

    fun moveNode(chatId: Long, delta: Offset) {
        val node = nodes[chatId]
        if (node != null) {
            node.position += delta / cameraZoom
            AnalyticsManager.trackEvent("captures_moved")
        }
    }

    fun toggleTelegram(enabled: Boolean) {
        if (enabled) {
            manager.initClient()
        } else {
            manager.stopClient()
        }
        saveSpatialState()
    }

    fun toggleChatVisibility(chatId: Long, visible: Boolean) {
        if (visible) {
            if (!visibleChatIds.contains(chatId)) visibleChatIds.add(chatId)
        } else {
            visibleChatIds.remove(chatId)
            closeChat(chatId)
        }
        updateSpatialModel(manager.chatIds.value)
        saveSpatialState()
    }

    fun moveMessageNode(chatId: Long, messageId: Long, delta: Offset, zoom: Float) {
        val chatMap = messageNodes[chatId]
        val maxZ = chatMap?.values?.maxOfOrNull { it.zIndex } ?: 0f
        
        val selected = selectedMessageIds[chatId]
        if (selected?.contains(messageId) == true) {
            selected.forEach { id ->
                val node = getMessageNode(chatId, id)
                node.position += delta / zoom
                node.zIndex = maxZ + 1f
            }
        } else {
            val node = getMessageNode(chatId, messageId)
            node.position += delta / zoom
            node.zIndex = maxZ + 1f
        }
    }

    fun toggleMessageSelection(chatId: Long, messageId: Long) {
        val selected = selectedMessageIds.getOrPut(chatId) { mutableStateListOf() }
        if (selected.contains(messageId)) {
            selected.remove(messageId)
        } else {
            selected.add(messageId)
        }
    }

    fun clearMessageSelection(chatId: Long) {
        selectedMessageIds[chatId]?.clear()
    }

    fun onInternalTransform(chatId: Long, zoomDelta: Float, panDelta: Offset, centroid: Offset) {
        val window = openWindows.find { it.chatId == chatId } ?: return
        if (zoomDelta != 1f || panDelta != Offset.Zero) {
            AnalyticsManager.trackEvent("spatial_interactions")
        }
        val oldZoom = window.localCameraZoom
        val newZoom = (window.localCameraZoom * zoomDelta).coerceIn(0.1f, 10f)
        val localPoint = (centroid / oldZoom) - window.localCameraOffset
        window.localCameraZoom = newZoom
        window.localCameraOffset = (centroid / newZoom) - localPoint
        window.localCameraOffset += panDelta / newZoom
    }

    fun toggleFullScreen(chatId: Long) {
        focusedChatId = chatId
        messageDetailScale = 1f
        messageDetailOffset = Offset.Zero
        isInputBarForcedVisible = false
    }

    fun openLeadDeveloperChat() {
        manager.searchPublicChat("morcelagox") { chat ->
            if (chat != null) {
                viewModelScope.launch(Dispatchers.Main) {
                    selectChat(chat.id)
                    isAboutVisible = false
                }
            }
        }
    }

    fun centerOn(worldPos: Offset, screenW: Float, screenH: Float) {
        // Calculate the offset required to put worldPos at (screenW/2, screenH/2)
        // (worldPos.x + cameraOffset.x) * cameraZoom = screenW / 2
        cameraOffset = Offset(
            (screenW / 2f) / cameraZoom - worldPos.x,
            (screenH / 2f) / cameraZoom - worldPos.y
        )
        saveSpatialState()
    }

    private var saveJob: Job? = null
    fun debouncedSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(1000)
            saveSpatialState()
        }
    }

    private fun saveSpatialState() {
        try {
            val state = JSONObject()
            val nodesArray = JSONArray()
            nodes.forEach { (id, nodeState) ->
                val nodeObj = JSONObject()
                nodeObj.put("id", id)
                nodeObj.put("x", nodeState.position.x.toDouble())
                nodeObj.put("y", nodeState.position.y.toDouble())
                nodesArray.put(nodeObj)
            }
            state.put("nodes", nodesArray)

            val windowsArray = JSONArray()
            openWindows.forEach { win ->
                val winObj = JSONObject()
                winObj.put("id", win.chatId)
                winObj.put("x", win.worldPosition.x.toDouble())
                winObj.put("y", win.worldPosition.y.toDouble())
                winObj.put("w", win.width.toDouble())
                winObj.put("h", win.height.toDouble())
                winObj.put("fs", win.isFullScreen)
                winObj.put("lx", win.localCameraOffset.x.toDouble())
                winObj.put("ly", win.localCameraOffset.y.toDouble())
                winObj.put("lz", win.localCameraZoom.toDouble())
                windowsArray.put(winObj)
            }
            state.put("windows", windowsArray)
            
            // Save System Nodes
            val systemNodes = JSONObject()
            systemNodes.put("settingsX", settingsNodePos.x.toDouble())
            systemNodes.put("settingsY", settingsNodePos.y.toDouble())
            state.put("systemNodes", systemNodes)

            state.put("camX", cameraOffset.x.toDouble())
            state.put("camY", cameraOffset.y.toDouble())
            state.put("zoom", cameraZoom.toDouble())
            state.put("opacity", windowOpacity.toDouble())
            state.put("msgOpacity", messageOpacity.toDouble())
            state.put("focusMode", focusMode)
            state.put("zAxis", zAxisEnabled)
            state.put("renderPercentage", renderPercentage.toDouble())
            state.put("textScaling", isDynamicTextScalingEnabled)
            state.put("spatialChat", isSpatialChatEnabled)
            state.put("spatialChatDetail", isSpatialChatDetailEnabled)
            state.put("offScreenIndicators", isOffScreenIndicatorEnabled)
            state.put("staticButtons", isStaticButtonsEnabled)
            state.put("noRenderingConstraints", noRenderingConstraints)
            
            val visibleArray = JSONArray()
            visibleChatIds.forEach { visibleArray.put(it) }
            state.put("visibleChatIds", visibleArray)
            state.put("isFirstTelegramActivation", isFirstTelegramActivation)

            val msgNodesObj = JSONObject()
            messageNodes.forEach { (chatId, nodesMap) ->
                val chatNodesArray = JSONArray()
                nodesMap.forEach { (msgId, node) ->
                    val nodeObj = JSONObject()
                    nodeObj.put("id", msgId)
                    nodeObj.put("x", node.position.x.toDouble())
                    nodeObj.put("y", node.position.y.toDouble())
                    nodeObj.put("z", node.zIndex.toDouble())
                    chatNodesArray.put(nodeObj)
                }
                msgNodesObj.put(chatId.toString(), chatNodesArray)
            }
            state.put("messageNodes", msgNodesObj)

            prefs.edit().putString("json_state", state.toString()).apply()
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun loadSpatialState() {
        val jsonString = prefs.getString("json_state", null)
        val wUri = prefs.getString("wallpaper_uri", null)
        if (wUri != null) wallpaperUri = Uri.parse(wUri)

        if (jsonString == null) {
            // New user or cleared data
            return
        }

        try {
            val state = JSONObject(jsonString)
            windowOpacity = state.optDouble("opacity", 0.85).toFloat()
            messageOpacity = state.optDouble("msgOpacity", 1.0).toFloat()
            focusMode = state.optBoolean("focusMode", false)
            zAxisEnabled = state.optBoolean("zAxis", false)
            renderPercentage = state.optDouble("renderPercentage", 0.01).toFloat()
            isDynamicTextScalingEnabled = state.optBoolean("textScaling", false)
            isSpatialChatEnabled = state.optBoolean("spatialChat", false)
            isSpatialChatDetailEnabled = state.optBoolean("spatialChatDetail", false)
            isOffScreenIndicatorEnabled = state.optBoolean("offScreenIndicators", true)
            isStaticButtonsEnabled = state.optBoolean("staticButtons", false)
            noRenderingConstraints = state.optBoolean("noRenderingConstraints", false)
            isFirstTelegramActivation = state.optBoolean("isFirstTelegramActivation", true)
            
            val visibleArray = state.optJSONArray("visibleChatIds")
            visibleChatIds.clear()
            if (visibleArray != null) {
                for (i in 0 until visibleArray.length()) {
                    visibleChatIds.add(visibleArray.getLong(i))
                }
            }
            
            val mNodesObj = state.optJSONObject("messageNodes")
            if (mNodesObj != null) {
                mNodesObj.keys().forEach { chatIdStr ->
                    val chatId = chatIdStr.toLong()
                    val chatMap = mutableStateMapOf<Long, MessageNodeState>()
                    val arr = mNodesObj.getJSONArray(chatIdStr)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val msgId = obj.getLong("id")
                        val node = MessageNodeState(msgId, chatId, Offset(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat()))
                        node.zIndex = obj.optDouble("z", 0.0).toFloat()
                        chatMap[msgId] = node
                    }
                    messageNodes[chatId] = chatMap
                }
            }

            val nodesArray = state.optJSONArray("nodes")
            if (nodesArray != null) {
                for (i in 0 until nodesArray.length()) {
                    val obj = nodesArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    nodes[id] = ChatNodeState(id, Offset(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat()))
                }
            }

            val windowsArray = state.optJSONArray("windows")
            if (windowsArray != null) {
                openWindows.clear()
                for (i in 0 until windowsArray.length()) {
                    val obj = windowsArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    val window = ChatWindowState(
                        chatId = id,
                        initialPosition = Offset(obj.getDouble("x").toFloat(), obj.getDouble("y").toFloat()),
                        initialWidth = obj.optDouble("w", 500.0).toFloat(),
                        initialHeight = obj.optDouble("h", 700.0).toFloat()
                    )
                    window.isFullScreen = obj.optBoolean("fs", false)
                    window.localCameraOffset = Offset(
                        obj.optDouble("lx", 0.0).toFloat(),
                        obj.optDouble("ly", 0.0).toFloat()
                    )
                    window.localCameraZoom = obj.optDouble("lz", 1.0).toFloat()
                    openWindows.add(window)
                    manager.loadMessages(id)
                }
            }

            // Load System Nodes
            val sysNodes = state.optJSONObject("systemNodes")
            if (sysNodes != null) {
                settingsNodePos = Offset(sysNodes.optDouble("settingsX", 50.0).toFloat(), sysNodes.optDouble("settingsY", 50.0).toFloat())
            }

            cameraOffset = Offset(state.optDouble("camX", 0.0).toFloat(), state.optDouble("camY", 0.0).toFloat())
            cameraZoom = state.optDouble("zoom", 1.0).toFloat()
        } catch (e: Exception) { e.printStackTrace() }
    }
}
