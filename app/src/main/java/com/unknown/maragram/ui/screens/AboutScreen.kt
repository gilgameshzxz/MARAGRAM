package com.unknown.maragram.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.unknown.maragram.core.AnalyticsManager
import com.unknown.maragram.ui.*
import org.drinkless.tdlib.TdApi
import kotlinx.coroutines.delay
import java.util.Locale
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(viewModel: ZuiViewModel, onBack: () -> Unit) {
    var selectedFile by remember { mutableStateOf<LegalFile?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    
    var morcelagoxChat by remember { mutableStateOf<TdApi.Chat?>(null) }

    val totalUsers by AnalyticsManager.totalUsers.collectAsState()
    val milestoneReached by AnalyticsManager.milestoneReached.collectAsState()
    val milestone = 25000
    val progress = (totalUsers.toFloat() / milestone.toFloat()).coerceIn(0f, 1f)

    val legalFiles = listOf(
        LegalFile(
            name = "COPYRIGHT.txt",
            description = "Ownership and all rights reserved notice.",
            content = """
                # COPYRIGHT NOTICE
                © 2026 MORCELAGOX / Maragram.
                ALL RIGHTS RESERVED.

                Maragram's original source code, original software architecture, original interface designs (ZUI), original graphics, documentation, and other original works created for the project are protected by applicable international copyright laws.

                MORCELAGOX is the project identity used for the public development and distribution of Maragram. 
                
                No part of this software may be reproduced, distributed, or transmitted in any form or by any means, including photocopying, recording, or other electronic or mechanical methods, without the prior written permission of the owner.
            """.trimIndent()
        ),
        LegalFile(
            name = "LICENSE.txt",
            description = "Proprietary software license and usage terms.",
            content = """
                # PROPRIETARY LICENSE
                This software is provided for personal use only. Use of this software signifies your agreement to these terms.

                STRICTLY PROHIBITED ACTIONS:
                1. Reverse engineering, decompilation, or disassembly of the binary.
                2. Redistribution, sale, or sub-licensing of the APK or any modified versions.
                3. Extraction and reuse of original assets, including icons and UI components.
                4. Commercial exploitation of the "Spatial Chat Space" concept without written consent.

                ENFORCEMENT:
                Any violation of these terms will be met with immediate legal action to the fullest extent permitted by law. We actively monitor for unauthorized clones and derivative works.
            """.trimIndent()
        ),
        LegalFile(
            name = "IP_PROTECTION.txt",
            description = "Intellectual Property and Innovation protection.",
            content = """
                # INTELLECTUAL PROPERTY (IP)
                The "Universe Workspace", "Spatial Chat Canvas", and "Zoomable Message Architecture" are unique innovations developed exclusively for Maragram. 

                These concepts, including the specific spatial interaction models and 2D canvas messaging layouts, are protected as original design works and proprietary trade secrets. 

                MORCELAGOX reserves all rights to these innovations. Any attempt to replicate, clone, or "skin" these specific interaction models in competing software will be considered a violation of intellectual property rights and will be contested in court.
            """.trimIndent()
        )
    )

    LaunchedEffect(Unit) {
        AnalyticsManager.trackEvent("about_page_opened")
        
        viewModel.manager.searchPublicChat("morcelagox") { chat ->
            morcelagoxChat = chat
        }

        // Live polling while About screen is open
        while (true) {
            AnalyticsManager.fetchStats()
            delay(10000) // 10 seconds
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ABOUT MARAGRAM", style = LocalTextStyle.current.copy(fontFamily = JetBrainsMono, fontSize = 14.sp, letterSpacing = 2.sp)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Maragram is an independent project exploring a different way of communicating — spatially, visually, and beyond the traditional chat timeline.",
                color = Color.Gray,
                fontFamily = Inter,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Lead Developer Card
            Text(
                text = "LEAD DEVELOPER",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                onClick = { viewModel.openLeadDeveloperChat() },
                color = Color(0xFF0F0F0F),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val downloadedFiles by viewModel.manager.downloadedFiles.collectAsState()
                    
                    morcelagoxChat?.let { chat ->
                        ChatAvatar(chat, viewModel.manager, downloadedFiles, size = 56)
                    } ?: Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("M", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text("ABDALLAH ALSAYED", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("@morcelagox", color = Color.Gray, fontSize = 12.sp, fontFamily = JetBrainsMono)
                    }
                    
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Community Growth (Tracker)
            Surface(
                color = Color(0xFF0A0A0A),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "MILESTONE PROGRESS",
                        fontFamily = JetBrainsMono,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        letterSpacing = 4.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "$totalUsers / $milestone USERS",
                        fontFamily = Inter,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Light,
                        color = Color.White
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.1f)
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            text = "${String.format(Locale.US, "%.2f", progress * 100)}%",
                            fontFamily = JetBrainsMono,
                            fontSize = 11.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (milestoneReached) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "🎉 $milestone USERS REACHED",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            fontFamily = JetBrainsMono
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            // Support Section
            Text(
                text = "SUPPORT DEVELOPMENT",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))

            CryptoAddress(
                label = "USDT — TRON (TRC-20)", 
                address = "TUiL3AArsGwSatA7JobRJsw27BH33zfoKR",
                onCopy = { clipboardManager.setText(AnnotatedString(it)) }
            )
            Spacer(modifier = Modifier.height(12.dp))
            CryptoAddress(
                label = "Solana Tokens", 
                address = "C7Eb6oPej1SHYniHWFD3K3yFWqSUVTH1KWH5uxUuRujZ",
                onCopy = { clipboardManager.setText(AnnotatedString(it)) }
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Privacy & Legal
            LegalSection(legalFiles) { selectedFile = it }

            Spacer(modifier = Modifier.height(48.dp))

            SupportAction(
                icon = Icons.Default.Share,
                title = "Share Maragram",
                subtitle = "Invite others to the universe"
            ) {
                AnalyticsManager.trackEvent("share_clicked")
            }

            Spacer(modifier = Modifier.height(64.dp))

            // Contributors Section
            Text(
                text = "CONTRIBUTORS",
                fontFamily = JetBrainsMono,
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 2.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("ALAM ANU3EMI", color = Color.White, fontFamily = Inter, fontSize = 14.sp)
            Text("OMARU BIN KALTHOM", color = Color.White, fontFamily = Inter, fontSize = 14.sp)
            Text("?????", color = Color.White, fontFamily = Inter, fontSize = 14.sp)
            Text("TANE VAN WIFFEREN", color = Color.White, fontFamily = Inter, fontSize = 14.sp)
            Text("AIMER LOVE FOREVER", color = Color.White, fontFamily = Inter, fontSize = 14.sp)
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    if (selectedFile != null) {
        AlertDialog(
            onDismissRequest = { selectedFile = null },
            confirmButton = {
                TextButton(onClick = { selectedFile = null }) {
                    Text("CLOSE", color = Color.White, fontFamily = JetBrainsMono)
                }
            },
            title = {
                Text(
                    text = selectedFile!!.name,
                    color = Color.White,
                    fontFamily = JetBrainsMono,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = selectedFile!!.content,
                        color = Color.Gray,
                        fontFamily = Inter,
                        fontSize = 13.sp,
                        lineHeight = 20.sp
                    )
                }
            },
            containerColor = Color(0xFF111111),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

data class LegalFile(val name: String, val description: String, val content: String)

@Composable
fun CryptoAddress(label: String, address: String, onCopy: (String) -> Unit) {
    Surface(
        color = Color(0xFF0F0F0F),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, color = Color.Gray, fontSize = 9.sp, fontFamily = JetBrainsMono)
                Spacer(modifier = Modifier.height(4.dp))
                SelectionContainer {
                    Text(address, color = Color.White, fontSize = 11.sp, fontFamily = JetBrainsMono)
                }
            }
            IconButton(onClick = { onCopy(address) }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun LegalSection(files: List<LegalFile>, onOpenFile: (LegalFile) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "PRIVACY & LEGAL",
            fontFamily = JetBrainsMono,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.5f),
            letterSpacing = 2.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Privacy Note: Maragram uses anonymous analytics to track community growth. Your identity remains entirely private.",
            color = Color.Gray,
            fontSize = 11.sp,
            lineHeight = 18.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "LEGAL DOCUMENTS",
            fontFamily = JetBrainsMono,
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.3f),
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(12.dp))

        files.forEach { file ->
            Surface(
                onClick = { onOpenFile(file) },
                color = Color(0xFF0F0F0F),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Description, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(file.name, color = Color.White, fontSize = 13.sp, fontFamily = JetBrainsMono)
                        Text(file.description, color = Color.Gray, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.White.copy(alpha = 0.2f), modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
fun SupportAction(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(subtitle, color = Color.Gray, fontSize = 11.sp)
            }
        }
    }
}
