package com.unknown.maragram.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.unknown.maragram.R
import com.unknown.maragram.core.AnalyticsManager
import com.unknown.maragram.core.LanguageManager
import com.unknown.maragram.core.SupportedLanguage
import com.unknown.maragram.core.SystemConcepts
import com.unknown.maragram.core.get
import com.unknown.maragram.engine.TelegramManager
import com.unknown.maragram.ui.*
import com.unknown.maragram.ui.screens.AboutScreen
import org.drinkless.tdlib.TdApi
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ZuiScreen(manager: TelegramManager, viewModel: ZuiViewModel) {
    if (!viewModel.isReady) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Color.White)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Synchronizing Universe...", color = Color.Gray, fontSize = 10.sp, fontFamily = JetBrainsMono)
            }
        }
        return
    }

    val chats by manager.chats.collectAsState()
    val downloadedFiles by manager.downloadedFiles.collectAsState()
    val chatIds by manager.chatIds.collectAsState()
    val authState by manager.authState.collectAsState(initial = null)
    val isAuthorized = authState is TdApi.AuthorizationStateReady

    val isArabic = LanguageManager.selectedLanguage == SupportedLanguage.ARABIC
    val layoutDirection = if (isArabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr

    val visibleCount = (chatIds.size * viewModel.renderPercentage).toInt().coerceAtLeast(1)
    val visibleIds = chatIds.take(visibleCount).toSet()

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            val screenWidth = constraints.maxWidth.toFloat()
            val screenHeight = constraints.maxHeight.toFloat()
            // Main Workspace Content
            AnimatedVisibility(
                visible = !viewModel.isAboutVisible && !viewModel.isSettingsVisible && viewModel.focusedChatId == null,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_universe_bg),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    
                    if (viewModel.wallpaperUri != null) {
                        AsyncImage(
                            model = viewModel.wallpaperUri,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().alpha(0.3f),
                            contentScale = ContentScale.Crop
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val anyFullScreen = viewModel.openWindows.any { it.isFullScreen }
                                        if (event.changes.size >= 2 && !anyFullScreen) {
                                            val zoom = event.calculateZoom()
                                            val pan = event.calculatePan()
                                            val centroid = event.calculateCentroid(useCurrent = false)
                                            
                                            if (zoom != 1f || pan != Offset.Zero) {
                                                viewModel.onTransform(zoom, pan, centroid)
                                                if (zoom != 1f) AnalyticsManager.trackEvent("zoom_actions")
                                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                                            }
                                        }
                                    }
                                }
                            }
                            .pointerInput(Unit) {
                                detectTransformGestures(
                                    onGesture = { centroid, pan, zoom, _ ->
                                        val anyFullScreen = viewModel.openWindows.any { it.isFullScreen }
                                        if (!anyFullScreen) {
                                            viewModel.onTransform(zoom, pan, centroid)
                                        }
                                    }
                                )
                            }
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Release) {
                                            viewModel.onInteractionEnd()
                                        }
                                    }
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = viewModel.cameraZoom.coerceIn(0.01f, 20f)
                                    scaleY = viewModel.cameraZoom.coerceIn(0.01f, 20f)
                                    translationX = viewModel.cameraOffset.x * viewModel.cameraZoom
                                    translationY = viewModel.cameraOffset.y * viewModel.cameraZoom
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                        ) {
                            val viewportWidth = screenWidth / viewModel.cameraZoom
                            val viewportHeight = screenHeight / viewModel.cameraZoom
                            val viewportRect = androidx.compose.ui.geometry.Rect(
                                -viewModel.cameraOffset.x - viewportWidth * 0.5f,
                                -viewModel.cameraOffset.y - viewportHeight * 0.5f,
                                -viewModel.cameraOffset.x + viewportWidth * 1.5f,
                                -viewModel.cameraOffset.y + viewportHeight * 1.5f
                            )

                            // Render System Nodes (Permanent)
                            if (!viewModel.isStaticButtonsEnabled) {
                                SystemNode(
                                    position = viewModel.settingsNodePos,
                                    icon = Icons.Default.Settings,
                                    color = Color.White,
                                    onClick = { viewModel.isSettingsVisible = true },
                                    onMove = { delta -> viewModel.settingsNodePos += delta / viewModel.cameraZoom }
                                )
                            }

                            // Render Chat Nodes
                            viewModel.nodes.values.forEach { nodeState ->
                                // Expanded culling margin for chat nodes to prevent disappearance
                                val isVisible = viewModel.noRenderingConstraints || (
                                    nodeState.id in visibleIds && 
                                    nodeState.position.x > viewportRect.left - 1000f &&
                                    nodeState.position.x < viewportRect.right + 1000f &&
                                    nodeState.position.y > viewportRect.top - 1000f &&
                                    nodeState.position.y < viewportRect.bottom + 1000f
                                )

                                if (isVisible) {
                                    key(nodeState.id) {
                                        val chat = chats[nodeState.id]
                                        if (chat != null) {
                                            ChatNode(
                                                chat = chat,
                                                nodeState = nodeState,
                                                zoom = viewModel.cameraZoom,
                                                isSelected = viewModel.selectedChatId == nodeState.id,
                                                searchQuery = viewModel.searchQuery,
                                                downloadedFiles = downloadedFiles,
                                                manager = manager,
                                                viewModel = viewModel,
                                                onSelect = { viewModel.selectChat(nodeState.id) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Render Chat Windows
                            viewModel.openWindows.forEach { windowState ->
                                key(windowState.chatId) {
                                    val chat = chats[windowState.chatId]
                                    if (chat != null) {
                                        ChatWindow(
                                            chat = chat,
                                            windowState = windowState,
                                            opacity = viewModel.windowOpacity,
                                            manager = manager,
                                            downloadedFiles = downloadedFiles,
                                            viewModel = viewModel,
                                            screenWidth = screenWidth,
                                            screenHeight = screenHeight,
                                            onMove = { delta: Offset -> viewModel.moveWindow(windowState.chatId, delta) },
                                            onResize = { delta: Offset -> viewModel.resizeWindow(windowState.chatId, delta) },
                                            onFocus = { viewModel.focusWindow(windowState.chatId) },
                                            onInteractionEnd = { viewModel.debouncedSave() },
                                            onClose = { viewModel.closeChat(windowState.chatId) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Navigation Overlay: Grouped Off-Screen Indicators
                    if (viewModel.isOffScreenIndicatorEnabled) {
                        GroupedOffScreenIndicators(viewModel, screenWidth, screenHeight, isAuthorized, visibleIds, chats)
                    }

                    if (viewModel.isStaticButtonsEnabled) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .statusBarsPadding()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Settings Button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable { viewModel.isSettingsVisible = true }
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Settings, null, tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }

            // Full Page About
            AnimatedVisibility(
                visible = viewModel.isAboutVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                AboutScreen(viewModel = viewModel, onBack = { viewModel.isAboutVisible = false })
            }

            // Full Page Settings
            AnimatedVisibility(
                visible = viewModel.isSettingsVisible && 
                         !viewModel.isTelegramAccountSettingsVisible && 
                         !viewModel.isWorkspaceSettingsVisible &&
                         !viewModel.isPluginsSettingsVisible &&
                         !viewModel.isBehaviorSettingsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                SettingsPage(viewModel, visibleCount, chatIds.size, manager, downloadedFiles)
            }

            // Workspace Settings Sub-page
            AnimatedVisibility(
                visible = viewModel.isWorkspaceSettingsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                WorkspaceSettingsPage(viewModel, visibleCount, chatIds.size)
            }

            // Plugins Settings Sub-page
            AnimatedVisibility(
                visible = viewModel.isPluginsSettingsVisible && 
                         !viewModel.isTelegramAccountSettingsVisible && 
                         !viewModel.isTelegramVisibilitySettingsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                PluginsSettingsPage(viewModel, manager, downloadedFiles)
            }

            // Behavior Settings Sub-page
            AnimatedVisibility(
                visible = viewModel.isBehaviorSettingsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                BehaviorSettingsPage(viewModel)
            }

            // Telegram Account Settings Sub-page
            AnimatedVisibility(
                visible = viewModel.isTelegramAccountSettingsVisible && !viewModel.isTelegramVisibilitySettingsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                TelegramAccountSettings(viewModel, manager, downloadedFiles)
            }

            // Telegram Visibility Settings Sub-page
            AnimatedVisibility(
                visible = viewModel.isTelegramVisibilitySettingsVisible,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                TelegramVisibilitySettingsPage(viewModel, manager, downloadedFiles)
            }

            // Full Screen Focused Chat
            AnimatedVisibility(
                visible = viewModel.focusedChatId != null,
                enter = scaleIn(initialScale = 0.9f) + fadeIn(),
                exit = scaleOut(targetScale = 0.9f) + fadeOut()
            ) {
                val fId = viewModel.focusedChatId
                if (fId != null) {
                    val fChat = chats[fId]
                    if (fChat != null) {
                        key(fId) {
                            FocusedChatScreen(
                                chat = fChat,
                                viewModel = viewModel,
                                manager = manager,
                                downloadedFiles = downloadedFiles,
                                onBack = { viewModel.focusedChatId = null }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SystemNode(
    position: Offset,
    icon: ImageVector,
    color: Color,
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    onClick: () -> Unit,
    onMove: (Offset) -> Unit
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(position.x.toInt(), position.y.toInt()) }
            .size(64.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onMove(dragAmount)
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            }
            .background(Color.Black.copy(alpha = 0.6f), shape)
            .border(1.dp, color.copy(alpha = 0.3f), shape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(32.dp))
    }
}

@Composable
fun GroupedOffScreenIndicators(
    viewModel: ZuiViewModel,
    screenWidth: Float,
    screenHeight: Float,
    isAuthorized: Boolean,
    visibleIds: Set<Long>,
    chats: Map<Long, TdApi.Chat>
) {
    val zoom = viewModel.cameraZoom
    val offset = viewModel.cameraOffset
    val margin = 24.dp
    val marginPx = with(LocalDensity.current) { margin.toPx() }

    val targets = mutableListOf<IndicatorTarget>()

    // Collect all off-screen positions
    if (isAuthorized) {
        viewModel.nodes.values.forEach { node ->
            if (node.id in visibleIds && chats.containsKey(node.id)) {
                targets.add(IndicatorTarget(node.position, Color.White.copy(alpha = 0.6f)))
            }
        }
        viewModel.openWindows.forEach { win ->
            if (chats.containsKey(win.chatId)) {
                targets.add(IndicatorTarget(win.worldPosition, Color.White))
            }
        }
    }

    targets.add(IndicatorTarget(viewModel.settingsNodePos, Color.White.copy(alpha = 0.8f)))
    
    val offScreenTargets = targets.map { target ->
        val screenX = (target.worldPos.x + offset.x) * zoom
        val screenY = (target.worldPos.y + offset.y) * zoom
        target.copy(screenPos = Offset(screenX, screenY))
    }.filter { 
        it.screenPos.x < 0 || it.screenPos.x > screenWidth || it.screenPos.y < 0 || it.screenPos.y > screenHeight 
    }

    if (offScreenTargets.isEmpty()) return

    // Grouping logic: Divide screen edges into slots
    // For simplicity, we can group by angle or by edge intersection point proximity
    val groups = mutableListOf<MutableList<IndicatorTarget>>()
    val threshold = 150f // pixels on edge

    offScreenTargets.forEach { target ->
        val edgePos = calculateEdgePosition(target.screenPos, screenWidth, screenHeight, marginPx)
        var found = false
        for (group in groups) {
            val groupEdgePos = calculateEdgePosition(group[0].screenPos, screenWidth, screenHeight, marginPx)
            if (edgePos.distanceTo(groupEdgePos) < threshold) {
                group.add(target)
                found = true
                break
            }
        }
        if (!found) groups.add(mutableListOf(target))
    }

    Box(modifier = Modifier.fillMaxSize()) {
        groups.forEach { group ->
            val representative = group[0]
            
            // Average world position for teleporting to cluster center
            val avgWorldX = group.map { it.worldPos.x }.average().toFloat()
            val avgWorldY = group.map { it.worldPos.y }.average().toFloat()
            
            val dx = representative.screenPos.x - (screenWidth / 2f)
            val dy = representative.screenPos.y - (screenHeight / 2f)
            val angle = Math.toDegrees(Math.atan2(dy.toDouble(), dx.toDouble()))

            val dist = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
            val baseSize = 52f
            val minSize = 28f
            val indicatorSize = (baseSize * (2500f / (dist + 2500f))).coerceAtLeast(minSize)
            
            // Dynamic margin to make the indicator touch the screen edge
            val dynamicMarginPx = with(LocalDensity.current) { (indicatorSize / 2f).dp.toPx() }
            val edgePos = calculateEdgePosition(representative.screenPos, screenWidth, screenHeight, dynamicMarginPx)

            Box(
                modifier = Modifier
                    .offset { IntOffset((edgePos.x - (indicatorSize.dp.toPx() / 2)).toInt(), (edgePos.y - (indicatorSize.dp.toPx() / 2)).toInt()) }
                    .size(indicatorSize.dp)
                    .graphicsLayer { rotationZ = angle.toFloat() }
                    .background(representative.color.copy(alpha = 0.2f), CircleShape)
                    .border(1.dp, representative.color.copy(alpha = 0.5f), CircleShape)
                    .clickable { viewModel.centerOn(Offset(avgWorldX, avgWorldY), screenWidth, screenHeight) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = representative.color,
                    modifier = Modifier.size((indicatorSize * 0.7f).dp)
                )
                if (group.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .background(Color.Red, CircleShape)
                            .padding(horizontal = 4.dp)
                    ) {
                        Text(group.size.toString(), color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class IndicatorTarget(
    val worldPos: Offset,
    val color: Color,
    val screenPos: Offset = Offset.Zero
)

fun calculateEdgePosition(screenTarget: Offset, screenW: Float, screenH: Float, margin: Float): Offset {
    val centerX = screenW / 2f
    val centerY = screenH / 2f
    val dx = screenTarget.x - centerX
    val dy = screenTarget.y - centerY
    val slope = dy / dx
    
    val horizontalLimit = screenW / 2f - margin
    val verticalLimit = screenH / 2f - margin
    
    val edgeX: Float
    val edgeY: Float
    
    if (Math.abs(dx) / horizontalLimit > Math.abs(dy) / verticalLimit) {
        edgeX = if (dx > 0) horizontalLimit else -horizontalLimit
        edgeY = edgeX * slope
    } else {
        edgeY = if (dy > 0) verticalLimit else -verticalLimit
        edgeX = edgeY / slope
    }
    
    return Offset(centerX + edgeX, centerY + edgeY)
}

fun Offset.distanceTo(other: Offset): Float {
    val dx = x - other.x
    val dy = y - other.y
    return Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
}

@Composable
fun SettingsPage(
    viewModel: ZuiViewModel, 
    visibleCount: Int, 
    totalChats: Int,
    manager: TelegramManager,
    downloadedFiles: Map<Int, String>
) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("SYSTEM SETTINGS", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.isSettingsVisible = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsCategoryButton(
                title = "WORKSPACE",
                subtitle = "Capacity, transparency & layout",
                icon = Icons.Default.Dashboard,
                onClick = { viewModel.isWorkspaceSettingsVisible = true }
            )

            SettingsCategoryButton(
                title = "PLUGINS",
                subtitle = "Telegram integration",
                icon = Icons.Default.Extension,
                onClick = { viewModel.isPluginsSettingsVisible = true }
            )

            SettingsCategoryButton(
                title = "BEHAVIOR",
                subtitle = "Workspace modes & interactions",
                icon = Icons.Default.Tune,
                onClick = { viewModel.isBehaviorSettingsVisible = true }
            )

            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { 
                    viewModel.isAboutVisible = true
                    viewModel.isSettingsVisible = false
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A), contentColor = Color.White),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("ABOUT MARAGRAM", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold))
            }
        }
    }
}

@Composable
fun SettingsCategoryButton(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color(0xFF111111),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(40.dp).background(Color(0xFF1A1A1A), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(20.dp))
            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                Text(subtitle, color = Color.Gray, fontSize = 11.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
fun WorkspaceSettingsPage(viewModel: ZuiViewModel, visibleCount: Int, totalChats: Int) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("WORKSPACE SETTINGS", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.isWorkspaceSettingsVisible = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            SettingHeader("WORKSPACE CAPACITY")
            Text(
                text = "RENDER LOAD: ${(viewModel.renderPercentage * 100).toInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = JetBrainsMono
            )
            Slider(
                value = viewModel.renderPercentage,
                onValueChange = { viewModel.renderPercentage = it },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            Text(
                text = "Showing $visibleCount of $totalChats chats",
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = JetBrainsMono
            )

            Spacer(modifier = Modifier.height(32.dp))

            SettingHeader("WINDOW APPEARANCE")
            Text(
                text = "WINDOW TRANSPARENCY: ${(viewModel.windowOpacity * 100).toInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = JetBrainsMono
            )
            Slider(
                value = viewModel.windowOpacity,
                onValueChange = { viewModel.windowOpacity = it },
                valueRange = 0.1f..1f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "MESSAGE TRANSPARENCY: ${(viewModel.messageOpacity * 100).toInt()}%",
                color = Color.White,
                fontSize = 12.sp,
                fontFamily = JetBrainsMono
            )
            Slider(
                value = viewModel.messageOpacity,
                onValueChange = { viewModel.messageOpacity = it },
                valueRange = 0.1f..1f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White)
            )
        }
    }
}

@Composable
fun PluginsSettingsPage(viewModel: ZuiViewModel, manager: TelegramManager, downloadedFiles: Map<Int, String>) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("PLUGIN SETTINGS", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.isPluginsSettingsVisible = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            SettingHeader("INTEGRATIONS")
            SettingToggle(
                title = "TELEGRAM INTEGRATION",
                subtitle = "Enable TDLib for chat universe",
                checked = true,
                onCheckedChange = { }
            )
            
            TextButton(
                onClick = { viewModel.isTelegramAccountSettingsVisible = true },
                modifier = Modifier.padding(start = 0.dp)
            ) {
                Text("MORE TELEGRAM SETTINGS", color = Color.White, fontSize = 11.sp, fontFamily = JetBrainsMono, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BehaviorSettingsPage(viewModel: ZuiViewModel) {
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("BEHAVIOR SETTINGS", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.isBehaviorSettingsVisible = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp)
        ) {
            SettingHeader("BEHAVIOR")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("WORKSPACE MODE", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(if (viewModel.isSpatialChatEnabled) "Spatial (Free movement)" else "Linear (Timeline)", color = Color.Gray, fontSize = 11.sp)
                }
                Button(
                    onClick = { viewModel.isSpatialChatEnabled = !viewModel.isSpatialChatEnabled },
                    colors = ButtonDefaults.buttonColors(containerColor = if (viewModel.isSpatialChatEnabled) Color.White else Color(0xFF222222))
                ) {
                    Text(if (viewModel.isSpatialChatEnabled) "SPATIAL" else "LINEAR", color = if (viewModel.isSpatialChatEnabled) Color.Black else Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            SettingToggle(
                title = "FOCUSED SPATIAL MODE",
                subtitle = "Enable spatial layout in full screen chat",
                checked = viewModel.isSpatialChatDetailEnabled,
                onCheckedChange = { viewModel.isSpatialChatDetailEnabled = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingToggle(
                title = "UNCONSTRAINED RENDERING",
                subtitle = "Bypass all viewport culling (Requires high-end GPU)",
                checked = viewModel.noRenderingConstraints,
                onCheckedChange = { viewModel.noRenderingConstraints = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingToggle(
                title = "DYNAMIC TEXT SCALING",
                subtitle = "Resize window to scale text size",
                checked = viewModel.isDynamicTextScalingEnabled,
                onCheckedChange = { viewModel.isDynamicTextScalingEnabled = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingToggle(
                title = "OFF-SCREEN INDICATORS",
                subtitle = "Arrows pointing to objects outside the view",
                checked = viewModel.isOffScreenIndicatorEnabled,
                onCheckedChange = { viewModel.isOffScreenIndicatorEnabled = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            SettingToggle(
                title = "STATIC SYSTEM BAR BUTTONS",
                subtitle = "Dock settings, add, and stream buttons under the screen tray",
                checked = viewModel.isStaticButtonsEnabled,
                onCheckedChange = { viewModel.isStaticButtonsEnabled = it }
            )
        }
    }
}

@Composable
fun SettingHeader(text: String) {
    Text(
        text = text,
        color = Color.White.copy(alpha = 0.4f),
        fontSize = 10.sp,
        fontFamily = JetBrainsMono,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 12.dp)
    )
}

@Composable
fun SettingToggle(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.Gray, fontSize = 11.sp)
        }
        Switch(
            checked = checked, 
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White)
        )
    }
}

@Composable
fun ChatNode(
    chat: TdApi.Chat,
    nodeState: ChatNodeState,
    zoom: Float,
    isSelected: Boolean,
    searchQuery: String,
    downloadedFiles: Map<Int, String>,
    manager: TelegramManager,
    viewModel: ZuiViewModel,
    onSelect: () -> Unit
) {
    val nodeWidth = 300.dp
    val nodeHeight = 80.dp
    
    val isSearching = searchQuery.isNotEmpty()
    val isMatch = chat.title.contains(searchQuery, ignoreCase = true)
    
    val hasActiveWindow = viewModel.openWindows.any { it.chatId == chat.id }
    val focusAlpha = if (viewModel.focusMode && !hasActiveWindow && !isSelected) 0.1f else 1f
    val zScale = if (viewModel.zAxisEnabled) 1.05f else 1f

    Box(
        modifier = Modifier
            .graphicsLayer {
                translationX = nodeState.position.x
                translationY = nodeState.position.y
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = zScale
                scaleY = zScale
                alpha = (if (isSearching && !isMatch) 0.15f else 1f) * focusAlpha
            }
            .requiredSize(width = nodeWidth, height = nodeHeight)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        viewModel.moveNode(chat.id, dragAmount)
                        viewModel.debouncedSave()
                    },
                    onDragEnd = { viewModel.onInteractionEnd() },
                    onDragCancel = { viewModel.onInteractionEnd() }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onSelect() })
            },
        contentAlignment = Alignment.CenterStart
    ) {
        if (zoom < 0.15f) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.5f))
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ChatAvatar(chat, manager, downloadedFiles, size = 56)
                Spacer(modifier = Modifier.width(12.dp))
                Surface(
                    modifier = Modifier.width(180.dp),
                    color = if (isSelected) Color.White else Color(0xFF111111).copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (isSelected) Color.White else Color.White.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text(
                            text = chat.title,
                            color = if (isSelected) Color.Black else Color.White,
                            fontSize = 14.sp,
                            fontFamily = Inter,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (chat.unreadCount > 0) {
                            Text(
                                text = "${chat.unreadCount} NEW",
                                color = if (isSelected) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                fontFamily = JetBrainsMono,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatWindow(
    chat: TdApi.Chat,
    windowState: ChatWindowState,
    opacity: Float,
    manager: TelegramManager,
    downloadedFiles: Map<Int, String>,
    viewModel: ZuiViewModel,
    screenWidth: Float,
    screenHeight: Float,
    onMove: (Offset) -> Unit,
    onResize: (Offset) -> Unit,
    onFocus: () -> Unit,
    onInteractionEnd: () -> Unit,
    onClose: () -> Unit
) {
    val messagesMap by manager.messages.collectAsState()
    val chatMessages = remember(messagesMap, chat.id) { messagesMap[chat.id] ?: emptyList<TdApi.Message>() }
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    var wasAtBottom by remember { mutableStateOf(true) }
    var isFullScreen by remember { mutableStateOf(windowState.isFullScreen) }

    LaunchedEffect(windowState.isFullScreen) {
        isFullScreen = windowState.isFullScreen
    }

    val width = windowState.width
    val height = windowState.height
    val position = windowState.worldPosition
    
    val currentWidth = if (isFullScreen) 1000f else width
    val currentHeight = if (isFullScreen) 1400f else height

    val vScale = if (viewModel.isDynamicTextScalingEnabled) {
        val scaleH = currentHeight / 700f
        val scaleW = currentWidth / 500f
        ((scaleH + scaleW) / 2f).coerceIn(0.5f, 5f)
    } else 1f

    LaunchedEffect(listState.canScrollForward) {
        if (!listState.isScrollInProgress) {
            wasAtBottom = !listState.canScrollForward
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (wasAtBottom && chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.lastIndex)
        }
    }

    var isLoadingMore by remember { mutableStateOf(false) }
    val reachedTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(reachedTop) {
        if (reachedTop && chatMessages.isNotEmpty() && !isLoadingMore) {
            val oldestMessageId = chatMessages.first().id
            if (oldestMessageId != 0L) {
                isLoadingMore = true
                manager.loadMessages(chat.id, oldestMessageId)
            }
        }
    }

    LaunchedEffect(chatMessages.size) {
        isLoadingMore = false
    }

    Surface(
        modifier = if (isFullScreen) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .graphicsLayer {
                    translationX = position.x
                    translationY = position.y
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .requiredSize(width = width.dp, height = height.dp)
        }
        .shadow(if (isFullScreen) 0.dp else 32.dp, RoundedCornerShape(if (isFullScreen) 0.dp else 16.dp))
        .pointerInput(isFullScreen) {
            if (!isFullScreen) {
                detectTapGestures(onPress = { onFocus() })
            }
        }
        .imePadding(),
        shape = RoundedCornerShape(if (isFullScreen) 0.dp else 16.dp),
        color = Color(0xFF0A0A0A).copy(alpha = if (isFullScreen) 1.0f else opacity),
        border = if (isFullScreen) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF151515))
                    .pointerInput(isFullScreen) {
                        if (!isFullScreen) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                onMove(dragAmount)
                            }
                        }
                    }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ChatAvatar(chat, manager, downloadedFiles, size = 40)
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = chat.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = (16 * vScale).sp,
                    fontFamily = Inter,
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = { viewModel.toggleFullScreen(chat.id) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Fullscreen,
                        contentDescription = "Full Screen",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            Box(modifier = Modifier.weight(1f)) {
                if (viewModel.isSpatialChatEnabled) {
                    SpatialChatCanvas(chat, windowState, chatMessages, viewModel, manager, vScale, isFullScreen)
                } else {
                    val selectedIds = viewModel.selectedMessageIds[chat.id] ?: emptyList<Long>()
                    
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        reverseLayout = false,
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = PaddingValues(vertical = 24.dp)
                    ) {
                        for (msg in chatMessages) {
                            item(key = msg.id) {
                                val isOutgoing = msg.id < 0 || msg.isOutgoing 
                                val isSelected = selectedIds.contains(msg.id)
                                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                                val time = timeFormat.format(Date(msg.date.toLong() * 1000))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(),
                                    contentAlignment = if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart
                                ) {
                                    Box(modifier = Modifier.matchParentSize()) {
                                        if (isOutgoing) {
                                            Box(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(2.dp).background(Color.White.copy(alpha = 0.08f)))
                                            Box(modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-11).dp).size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                                        } else {
                                            Box(modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(2.dp).background(Color.White.copy(alpha = 0.08f)))
                                            Box(modifier = Modifier.align(Alignment.CenterStart).offset(x = 11.dp).size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.3f)))
                                        }
                                    }

                                    Surface(
                                        color = if (isSelected) Color.White else if (isOutgoing) Color(0xFF222222) else Color(0xFF1A1A1A),
                                        shape = RoundedCornerShape(12.dp),
                                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                                        modifier = Modifier
                                            .fillMaxWidth() 
                                            .padding(horizontal = 24.dp, vertical = 6.dp)
                                            .alpha(viewModel.messageOpacity)
                                            .pointerInput(msg.id) {
                                                detectTapGestures(
                                                    onLongPress = { viewModel.toggleMessageSelection(chat.id, msg.id) }
                                                )
                                            }
                                    ) {
                                        Column {
                                            MaragramMessageContent(
                                                content = msg.content,
                                                isOutgoing = isSelected || isOutgoing,
                                                downloadedFiles = downloadedFiles,
                                                manager = manager,
                                                vScale = vScale,
                                                modifier = Modifier.padding(16.dp)
                                            )
                                            Text(
                                                text = time,
                                                color = (if (isSelected) Color.Black else if (isOutgoing) Color.Gray else Color.Gray).copy(alpha = 0.5f),
                                                fontSize = (9 * vScale).sp,
                                                fontFamily = JetBrainsMono,
                                                modifier = Modifier.align(Alignment.End).padding(end = 12.dp, bottom = 8.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                if (!isFullScreen) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                    ) {
                         IconButton(
                            onClick = { viewModel.toggleFullScreen(chat.id) },
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                        ) {
                            Icon(Icons.Default.Fullscreen, "Fullscreen", tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                    
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(64.dp)
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        onResize(dragAmount)
                                    },
                                    onDragEnd = { onInteractionEnd() },
                                    onDragCancel = { onInteractionEnd() }
                                )
                            }
                            .background(
                                Color.White.copy(alpha = 0.05f),
                                RoundedCornerShape(topStart = 32.dp, bottomEnd = 16.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.4f),
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .padding(bottom = 12.dp, end = 12.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = viewModel.isInputBarForcedVisible || (viewModel.selectedMessageIds[chat.id]?.isNotEmpty() == true),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Surface(
                    color = Color(0xFF151515),
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    shape = RoundedCornerShape(if (isFullScreen) 0.dp else 32.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                        androidx.compose.foundation.text.BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            textStyle = TextStyle(color = Color.White, fontSize = 15.sp, fontFamily = Inter),
                            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                            decorationBox = { innerTextField ->
                                if (inputText.isEmpty()) {
                                    Text("Write a message...", color = Color(0xFF444444), fontSize = 15.sp, fontFamily = Inter)
                                }
                                innerTextField()
                            }
                        )
                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank()) {
                                    manager.sendMessage(chat.id, inputText)
                                    inputText = ""
                                }
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpatialChatCanvas(
    chat: TdApi.Chat,
    windowState: ChatWindowState,
    messages: List<TdApi.Message>,
    viewModel: ZuiViewModel,
    manager: TelegramManager,
    vScale: Float,
    isFullScreen: Boolean = false
) {
    val selectedIds = viewModel.selectedMessageIds[chat.id] ?: emptyList<Long>()
    
    // When in full screen, we let the parent FocusedChatScreen handle the global zoom/offset
    val zoom = if (isFullScreen) 1f else windowState.localCameraZoom
    val offset = if (isFullScreen) Offset.Zero else windowState.localCameraOffset

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(chat.id, isFullScreen) {
                if (!isFullScreen) {
                    detectTransformGestures { centroid, pan, zoomDelta, _ ->
                        viewModel.onInternalTransform(chat.id, zoomDelta, pan, centroid)
                    }
                }
            }
            .pointerInput(chat.id) {
                detectTapGestures(onTap = { viewModel.clearMessageSelection(chat.id) })
            }
            .drawBehind {
                if (messages.size > 1) {
                    for (i in 0 until messages.size - 1) {
                        val node1 = viewModel.getMessageNode(chat.id, messages[i].id)
                        val node2 = viewModel.getMessageNode(chat.id, messages[i+1].id)
                        
                        val start = (node1.position + offset) * zoom
                        val end = (node2.position + offset) * zoom
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = start,
                            end = end,
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }
                }

                for (msg in messages) {
                    val replyTo = msg.replyTo
                    if (replyTo is TdApi.MessageReplyToMessage && (replyTo as TdApi.MessageReplyToMessage).chatId == chat.id) {
                        val fromNode = viewModel.getMessageNode(chat.id, msg.id)
                        val toNode = viewModel.getMessageNode(chat.id, (replyTo as TdApi.MessageReplyToMessage).messageId)
                        
                        val start = (fromNode.position + offset) * zoom
                        val end = (toNode.position + offset) * zoom
                        
                        drawLine(
                            color = Color.White.copy(alpha = 0.3f),
                            start = start,
                            end = end,
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }
            }
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offset.x * zoom
                    translationY = offset.y * zoom
                    transformOrigin = TransformOrigin(0f, 0f)
                    clip = false
                }
        ) {
            val chatMap = viewModel.messageNodes[chat.id]
            val sortedMessages = messages.sortedBy { (chatMap?.get(it.id)?.zIndex ?: 0f) as Float }
            
            // Viewport Culling DISABLED to fix disappearing nodes
            for (msg in sortedMessages) {
                key(msg.id) {
                    val node = viewModel.getMessageNode(chat.id, msg.id)
                    val isSelected = selectedIds.contains(msg.id)
                    MessageNode(
                        message = msg,
                        nodeState = node,
                        zoom = zoom,
                        isSelected = isSelected,
                        viewModel = viewModel,
                        manager = manager,
                        chatId = chat.id,
                        vScale = vScale
                    )
                }
            }
        }
    }
}

@Composable
fun MessageNode(
    message: TdApi.Message,
    nodeState: MessageNodeState,
    zoom: Float,
    isSelected: Boolean,
    viewModel: ZuiViewModel,
    manager: TelegramManager,
    chatId: Long,
    vScale: Float
) {
    val downloadedFiles by manager.downloadedFiles.collectAsState()
    
    // Level of Detail: Disabled to fix disappearing nodes

    Surface(
        modifier = Modifier
            .graphicsLayer {
                translationX = nodeState.position.x
                translationY = nodeState.position.y
            }
            .pointerInput(message.id) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        viewModel.moveMessageNode(chatId, message.id, dragAmount, zoom)
                        viewModel.debouncedSave()
                    }
                )
            }
            .pointerInput(message.id) {
                detectTapGestures(
                    onLongPress = { viewModel.toggleMessageSelection(chatId, message.id) }
                )
            }
            .alpha(viewModel.messageOpacity),
        color = if (isSelected) Color.White else if (message.isOutgoing) Color(0xFF222222) else Color(0xFF1A1A1A),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp, 
            if (isSelected) Color.White else Color.White.copy(alpha = 0.1f)
        )
    ) {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time = timeFormat.format(Date(message.date.toLong() * 1000))
        
        Box {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .offset(x = (-4).dp, y = 12.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.4f))
                    .border(1.dp, Color.Black.copy(alpha = 0.2f), CircleShape)
            )

            Column {
                MaragramMessageContent(
                    content = message.content,
                    isOutgoing = isSelected || message.isOutgoing, 
                    downloadedFiles = downloadedFiles,
                    manager = manager,
                    vScale = vScale,
                    modifier = Modifier.padding(12.dp),
                    isSpatial = true
                )
                Text(
                    text = time,
                    color = (if (isSelected) Color.Black else Color.Gray).copy(alpha = 0.4f),
                    fontSize = (8 * vScale).sp,
                    fontFamily = JetBrainsMono,
                    modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp)
                )
            }
        }
    }
}

@Composable
fun FocusedChatScreen(
    chat: TdApi.Chat,
    viewModel: ZuiViewModel,
    manager: TelegramManager,
    downloadedFiles: Map<Int, String>,
    onBack: () -> Unit
) {
    val messagesMap by manager.messages.collectAsState()
    val chatMessages = remember(messagesMap, chat.id) { messagesMap[chat.id] ?: emptyList() }
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var wasAtBottom by remember { mutableStateOf(true) }

    // Logic to keep focused screen updated and synced
    LaunchedEffect(chat.id) {
        manager.loadMessages(chat.id)
    }

    LaunchedEffect(listState.canScrollForward) {
        if (!listState.isScrollInProgress) {
            wasAtBottom = !listState.canScrollForward
        }
    }

    LaunchedEffect(chatMessages.size) {
        if (wasAtBottom && chatMessages.isNotEmpty()) {
            listState.animateScrollToItem(chatMessages.lastIndex)
        }
    }

    var isLoadingMore by remember { mutableStateOf(false) }
    val reachedTop by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    LaunchedEffect(reachedTop) {
        if (reachedTop && chatMessages.isNotEmpty() && !isLoadingMore) {
            val oldestMessageId = chatMessages.first().id
            if (oldestMessageId != 0L) {
                isLoadingMore = true
                manager.loadMessages(chat.id, oldestMessageId)
            }
        }
    }

    LaunchedEffect(chatMessages.size) {
        isLoadingMore = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. Bottom Layer: The Canvas (Truly Full Screen)
        // Applying transformation directly to a Box that contains the modes
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val oldScale = viewModel.messageDetailScale
                        val newScale = (oldScale * zoom).coerceIn(0.1f, 10f)
                        val worldPoint = (centroid / oldScale) - viewModel.messageDetailOffset
                        viewModel.messageDetailScale = newScale
                        viewModel.messageDetailOffset = (centroid / newScale) - worldPoint
                        viewModel.messageDetailOffset += pan / newScale
                    }
                }
                .graphicsLayer {
                    val s = viewModel.messageDetailScale.coerceIn(0.1f, 10f)
                    scaleX = s
                    scaleY = s
                    translationX = viewModel.messageDetailOffset.x * s
                    translationY = viewModel.messageDetailOffset.y * s
                    transformOrigin = TransformOrigin(0f, 0f)
                    // Explicitly disable clipping to ensure the "Universe" can flow outside original bounds
                    clip = false
                }
        ) {
            if (viewModel.isSpatialChatDetailEnabled) {
                SpatialChatCanvas(
                    chat = chat,
                    windowState = remember(chat.id) { ChatWindowState(chat.id) },
                    messages = chatMessages,
                    viewModel = viewModel,
                    manager = manager,
                    vScale = 1f,
                    isFullScreen = true
                )
            } else if (viewModel.noRenderingConstraints) {
                // Unconstrained Linear Mode: Render everything at once
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (msg in chatMessages) {
                        key(msg.id, msg.date) {
                            FocusedMessageItem(msg, chat, viewModel, downloadedFiles, manager)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 64.dp), 
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatMessages, key = { msg -> msg.id }) { msg ->
                        key(msg.id, msg.date) {
                            FocusedMessageItem(msg, chat, viewModel, downloadedFiles, manager)
                        }
                    }
                }
            }
        }

        // 2. Overlay Layer: Header
        Surface(
            color = Color(0xFF111111).copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                ChatAvatar(chat, manager, downloadedFiles, size = 28)
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = chat.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = Inter,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { viewModel.isInputBarForcedVisible = !viewModel.isInputBarForcedVisible },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (viewModel.isInputBarForcedVisible) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                        contentDescription = "Toggle Input",
                        tint = if (viewModel.isInputBarForcedVisible) Color.White else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 3. Overlay Layer: Input Pill
        AnimatedVisibility(
            visible = viewModel.isInputBarForcedVisible || (viewModel.selectedMessageIds[chat.id]?.isNotEmpty() == true),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).imePadding()
        ) {
            Surface(
                color = Color(0xFF151515).copy(alpha = 0.9f),
                modifier = Modifier.fillMaxWidth(0.6f).height(36.dp),
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp)
                ) {
                    androidx.compose.foundation.text.BasicTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = Inter),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
                        decorationBox = { innerTextField ->
                            if (inputText.isEmpty()) {
                                Text("Message", color = Color.Gray, fontSize = 12.sp)
                            }
                            innerTextField()
                        }
                    )
                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                manager.sendMessage(chat.id, inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun FocusedMessageItem(
    msg: TdApi.Message,
    chat: TdApi.Chat,
    viewModel: ZuiViewModel,
    downloadedFiles: Map<Int, String>,
    manager: TelegramManager
) {
    key(msg.id) {
        val isOutgoing = msg.isOutgoing
        val selectedIds = viewModel.selectedMessageIds[chat.id] ?: emptyList<Long>()
        val isSelected = selectedIds.contains(msg.id)
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val time = timeFormat.format(Date(msg.date.toLong() * 1000))
        
        Box(modifier = Modifier.fillMaxWidth()) {
            if (isOutgoing) {
                Box(modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(2.dp).background(Color.White.copy(alpha = 0.08f)))
                Box(modifier = Modifier.align(Alignment.CenterEnd).offset(x = (-8).dp).size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
            } else {
                Box(modifier = Modifier.align(Alignment.CenterStart).fillMaxHeight().width(2.dp).background(Color.White.copy(alpha = 0.08f)))
                Box(modifier = Modifier.align(Alignment.CenterStart).offset(x = 8.dp).size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)))
            }

            Surface(
                color = if (isSelected) Color.White else if (isOutgoing) Color(0xFF222222) else Color(0xFF1A1A1A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .align(if (isOutgoing) Alignment.CenterEnd else Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .alpha(viewModel.messageOpacity)
                    .pointerInput(msg.id) {
                        detectTapGestures(onLongPress = { viewModel.toggleMessageSelection(chat.id, msg.id) })
                    },
                border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
            ) {
                Column {
                    MaragramMessageContent(
                        content = msg.content,
                        isOutgoing = isSelected || isOutgoing,
                        downloadedFiles = downloadedFiles,
                        manager = manager,
                        vScale = 1f,
                        modifier = Modifier.padding(12.dp)
                    )
                    Text(
                        text = time,
                        color = (if (isSelected) Color.Black else if (isOutgoing) Color.Gray else Color.Gray).copy(alpha = 0.5f),
                        fontSize = 8.sp,
                        fontFamily = JetBrainsMono,
                        modifier = Modifier.align(Alignment.End).padding(end = 8.dp, bottom = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MaragramMessageContent(
    content: TdApi.MessageContent,
    isOutgoing: Boolean,
    downloadedFiles: Map<Int, String>,
    manager: TelegramManager,
    vScale: Float,
    modifier: Modifier = Modifier,
    isSpatial: Boolean = false
) {
    val textColor = if (isOutgoing) Color.Black else Color.White
    val fontSize = (if (isSpatial) 13 else 15) * vScale

    Column(modifier = modifier) {
        when (content.getConstructor()) {
            TdApi.MessageText.CONSTRUCTOR -> {
                val c = content as TdApi.MessageText
                Text(
                    text = c.text.text,
                    color = textColor,
                    fontSize = fontSize.sp,
                    fontFamily = Inter,
                    lineHeight = (fontSize * 1.4).sp
                )
            }
            TdApi.MessagePhoto.CONSTRUCTOR -> {
                val c = content as TdApi.MessagePhoto
                MediaView(
                    file = c.photo.sizes.lastOrNull()?.photo,
                    downloadedFiles = downloadedFiles,
                    manager = manager,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(8.dp))
                )
                if (c.caption.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = c.caption.text,
                        color = textColor,
                        fontSize = (fontSize * 0.9).sp,
                        fontFamily = Inter
                    )
                }
            }
            TdApi.MessageVideo.CONSTRUCTOR -> {
                val c = content as TdApi.MessageVideo
                Box(contentAlignment = Alignment.Center) {
                    MediaView(
                        file = c.video.thumbnail?.file,
                        downloadedFiles = downloadedFiles,
                        manager = manager,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(48.dp))
                }
                if (c.caption.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = c.caption.text,
                        color = textColor,
                        fontSize = (fontSize * 0.9).sp,
                        fontFamily = Inter
                    )
                }
            }
            TdApi.MessageSticker.CONSTRUCTOR -> {
                val c = content as TdApi.MessageSticker
                MediaView(
                    file = c.sticker.sticker,
                    downloadedFiles = downloadedFiles,
                    manager = manager,
                    modifier = Modifier.size((120 * vScale).dp)
                )
            }
            TdApi.MessageAnimation.CONSTRUCTOR -> {
                val c = content as TdApi.MessageAnimation
                MediaView(
                    file = c.animation.thumbnail?.file,
                    downloadedFiles = downloadedFiles,
                    manager = manager,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp).clip(RoundedCornerShape(8.dp))
                )
                if (c.caption.text.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = c.caption.text,
                        color = textColor,
                        fontSize = (fontSize * 0.9).sp,
                        fontFamily = Inter
                    )
                }
            }
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AttachFile, null, tint = textColor, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "[Media]",
                        color = textColor,
                        fontSize = fontSize.sp,
                        fontFamily = JetBrainsMono
                    )
                }
            }
        }
    }
}

@Composable
fun MediaView(
    file: TdApi.File?,
    downloadedFiles: Map<Int, String>,
    manager: TelegramManager,
    modifier: Modifier = Modifier
) {
    if (file == null) return
    
    var imagePath by remember(file.id, downloadedFiles) { 
        mutableStateOf(if (file.local.isDownloadingCompleted) file.local.path else downloadedFiles[file.id]) 
    }
    
    LaunchedEffect(file.id, imagePath) {
        if (imagePath == null) {
            manager.downloadFile(file.id)
        }
    }

    if (imagePath != null) {
        AsyncImage(
            model = imagePath,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.FillWidth
        )
    } else {
        Box(modifier = modifier.background(Color.White.copy(alpha = 0.05f)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
}

@Composable
fun TelegramAccountSettings(viewModel: ZuiViewModel, manager: TelegramManager, downloadedFiles: Map<Int, String>) {
    val authState by manager.authState.collectAsState(initial = null)
    val me by manager.me.collectAsState(initial = null)
    val constructorId = manager.getConstructor(authState)

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("TELEGRAM ACCOUNT", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.isTelegramAccountSettingsVisible = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (constructorId == TdApi.AuthorizationStateReady.CONSTRUCTOR && me != null) {
                // Profile Card
                Surface(
                    color = Color(0xFF111111),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(modifier = Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically) {
                        UserAvatar(user = me!!, manager = manager, downloadedFiles = downloadedFiles, size = 64)
                        Spacer(modifier = Modifier.width(20.dp))
                        Column {
                            Text("${me!!.firstName} ${me!!.lastName}", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Inter)
                            Text(me!!.phoneNumber, color = Color.Gray, fontSize = 12.sp, fontFamily = JetBrainsMono)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Button(
                    onClick = { viewModel.isTelegramVisibilitySettingsVisible = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("MANAGE UNIVERSE VISIBILITY", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { manager.addNewAccountSession() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B), contentColor = Color.White),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("CREATE NEW ACCOUNT SESSION", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { manager.logout() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF331111), contentColor = Color.Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("TERMINATE SESSION (LOGOUT)", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                }
            } else {
                // Auth Flow
                androidx.compose.animation.Crossfade(targetState = constructorId, label = "Settings Auth Transition") { targetConstructor ->
                    when (targetConstructor) {
                        TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                            var isBotMode by remember { mutableStateOf(false) }
                            var inputText by remember { mutableStateOf("") }
                            Column {
                                Text("INITIALIZE SESSION", color = Color.White, fontSize = 16.sp, letterSpacing = 4.sp, fontFamily = Inter)
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    FilterChip(
                                        selected = !isBotMode,
                                        onClick = { isBotMode = false; inputText = "" },
                                        label = { Text("PHONE NUMBER", fontSize = 10.sp, fontFamily = JetBrainsMono) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = Color.Black)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    FilterChip(
                                        selected = isBotMode,
                                        onClick = { isBotMode = true; inputText = "" },
                                        label = { Text("BOT TOKEN", fontSize = 10.sp, fontFamily = JetBrainsMono) },
                                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = Color.Black)
                                    )
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                PremiumTextField(
                                    value = inputText,
                                    onValueChange = { inputText = it },
                                    label = if (isBotMode) "BOT TOKEN" else "PHONE NUMBER",
                                    keyboardType = if (isBotMode) KeyboardType.Ascii else KeyboardType.Phone
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                Button(
                                    onClick = {
                                        if (isBotMode) {
                                            manager.sendBotToken(inputText.trim())
                                        } else {
                                            manager.sendPhoneNumber(inputText.trim())
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    enabled = inputText.isNotBlank()
                                ) {
                                    Text(if (isBotMode) "CONNECT BOT" else "CONNECT", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                            var code by remember { mutableStateOf("") }
                            Column {
                                Text("VERIFY ACCESS", color = Color.White, fontSize = 16.sp, letterSpacing = 4.sp, fontFamily = Inter)
                                Spacer(modifier = Modifier.height(24.dp))
                                PremiumTextField(
                                    value = code,
                                    onValueChange = { code = it },
                                    label = "AUTH CODE",
                                    keyboardType = KeyboardType.Number
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                Button(
                                    onClick = { manager.sendVerificationCode(code) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    enabled = code.isNotBlank()
                                ) {
                                    Text("AUTHORIZE", fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { manager.logout() }) {
                                    Text("CANCEL", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                        TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                            var password by remember { mutableStateOf("") }
                            Column {
                                Text("DECRYPT WORKSPACE", color = Color.White, fontSize = 16.sp, letterSpacing = 4.sp, fontFamily = Inter)
                                Spacer(modifier = Modifier.height(24.dp))
                                PremiumTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = "2FA PASSWORD"
                                )
                                Spacer(modifier = Modifier.height(32.dp))
                                Button(
                                    onClick = { manager.sendPassword(password) },
                                    modifier = Modifier.fillMaxWidth().height(48.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                    enabled = password.isNotBlank()
                                ) {
                                    Text("DECRYPT", fontWeight = FontWeight.Bold)
                                }
                                TextButton(onClick = { manager.logout() }) {
                                    Text("CANCEL", color = Color.Gray, fontSize = 10.sp)
                                }
                            }
                        }
                        else -> {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                CircularProgressIndicator(color = Color.White)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Syncing Auth State...", color = Color.Gray, fontSize = 10.sp, fontFamily = JetBrainsMono)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TelegramVisibilitySettingsPage(viewModel: ZuiViewModel, manager: TelegramManager, downloadedFiles: Map<Int, String>) {
    val chatIds by manager.chatIds.collectAsState()
    val chats by manager.chats.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    
    val filteredChatIds = remember(chatIds, chats, searchQuery) {
        chatIds.filter { id ->
            val chat = chats[id]
            chat?.title?.contains(searchQuery, ignoreCase = true) == true
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("UNIVERSE VISIBILITY", style = TextStyle(fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.isTelegramVisibilitySettingsVisible = false }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black, titleContentColor = Color.White, navigationIconContentColor = Color.White)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            // Search Bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search chats...", color = Color.Gray, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray) },
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF111111),
                    unfocusedContainerColor = Color(0xFF111111),
                    focusedIndicatorColor = Color.White,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(12.dp)
            )

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredChatIds, key = { it }) { id ->
                    val chat = chats[id]
                    if (chat != null) {
                        VisibilityChatItem(chat, viewModel, manager, downloadedFiles)
                    }
                }
            }
        }
    }
}

@Composable
fun VisibilityChatItem(chat: TdApi.Chat, viewModel: ZuiViewModel, manager: TelegramManager, downloadedFiles: Map<Int, String>) {
    val isVisible = viewModel.visibleChatIds.contains(chat.id)

    Surface(
        onClick = { viewModel.toggleChatVisibility(chat.id, !isVisible) },
        color = Color(0xFF111111),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isVisible) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(chat, manager, downloadedFiles, size = 48)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(chat.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (isVisible) "Visible in Universe" else "Hidden", color = if (isVisible) Color.Cyan else Color.Gray, fontSize = 10.sp, fontFamily = JetBrainsMono)
            }
            Checkbox(
                checked = isVisible,
                onCheckedChange = { viewModel.toggleChatVisibility(chat.id, it) },
                colors = CheckboxDefaults.colors(checkedColor = Color.White, uncheckedColor = Color.Gray, checkmarkColor = Color.Black)
            )
        }
    }
}