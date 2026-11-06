package com.unknown.maragram.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.unknown.maragram.core.LanguageManager
import com.unknown.maragram.core.SupportedLanguage
import com.unknown.maragram.core.SystemConcepts
import com.unknown.maragram.core.get
import com.unknown.maragram.engine.TelegramManager
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.TdApi.User

val Inter = FontFamily.SansSerif
val JetBrainsMono = FontFamily.Monospace

@Composable
fun ChatAvatar(chat: TdApi.Chat, manager: TelegramManager, downloadedFiles: Map<Int, String>, size: Int) {
    val photoFile = chat.photo?.small
    var imagePath: String? = null

    if (photoFile != null) {
        if (photoFile.local.isDownloadingCompleted) {
            imagePath = photoFile.local.path
        } else {
            imagePath = downloadedFiles[photoFile.id]
            if (imagePath == null) {
                manager.downloadFile(photoFile.id, 1)
            }
        }
    }

    Surface(
        modifier = Modifier.size(size.dp).clip(CircleShape),
        color = Color(0xFF222222),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        if (imagePath != null) {
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = chat.title.take(1).uppercase(),
                    color = Color.White,
                    fontSize = (size / 2.5).sp,
                    fontFamily = Inter
                )
            }
        }
    }
}

@Composable
fun AuthLayout(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    
    val isArabic = LanguageManager.selectedLanguage == SupportedLanguage.ARABIC
    val layoutDirection = if (isArabic) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides layoutDirection) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.animation.AnimatedVisibility(
                visible = visible,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(1000)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(1000)) { -40 }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.unknown.maragram.R.drawable.ic_app_logo),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(100.dp)
                            .padding(bottom = 24.dp)
                    )

                    if (onBack != null) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.align(Alignment.Start)
                        ) {
                            Text(SystemConcepts.RETURN.get(), style = TextStyle(fontFamily = JetBrainsMono, fontSize = 10.sp, letterSpacing = 2.sp, color = Color.Gray))
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        text = title.uppercase(),
                        style = TextStyle(
                            fontFamily = Inter,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Light,
                            letterSpacing = 6.sp,
                            color = Color.White
                        ),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = TextStyle(
                                fontFamily = JetBrainsMono,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraLight,
                                letterSpacing = 1.sp,
                                color = Color.Gray
                            ),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(48.dp))

            androidx.compose.animation.AnimatedVisibility(
                visible = visible,
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(1000, 500)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(1000, 500)) { 20 }
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun PremiumTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label.uppercase(), style = TextStyle(fontFamily = JetBrainsMono, fontSize = 9.sp, letterSpacing = 2.sp)) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.White,
            unfocusedIndicatorColor = Color.White.copy(alpha = 0.1f),
            cursorColor = Color.White
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun AuthRouter(authState: TdApi.AuthorizationState?, manager: TelegramManager) {
    val context = LocalContext.current.applicationContext
    val zuiViewModel: ZuiViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return ZuiViewModel(context, manager) as T
            }
        }
    )

    val constructorId = if (authState != null) manager.getConstructor(authState) else 0
    
    if (constructorId == TdApi.AuthorizationStateReady.CONSTRUCTOR && zuiViewModel.isReady) {
        com.unknown.maragram.ui.screens.ZuiScreen(manager, zuiViewModel)
    } else {
        androidx.compose.animation.Crossfade(targetState = constructorId, label = "Auth State Transition", animationSpec = androidx.compose.animation.core.tween(1000)) { targetConstructor ->
            when (targetConstructor) {
                TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                    var isBotMode by remember { mutableStateOf(false) }
                    var inputText by remember { mutableStateOf("") }
                    AuthLayout(title = SystemConcepts.INITIALIZE_SESSION.get()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            FilterChip(
                                selected = !isBotMode,
                                onClick = { isBotMode = false; inputText = "" },
                                label = { Text("PHONE NUMBER", fontSize = 10.sp, fontFamily = JetBrainsMono) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = Color.Black)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            FilterChip(
                                selected = isBotMode,
                                onClick = { isBotMode = true; inputText = "" },
                                label = { Text("BOT TOKEN", fontSize = 10.sp, fontFamily = JetBrainsMono) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White, selectedLabelColor = Color.Black)
                            )
                        }

                        PremiumTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = if (isBotMode) "BOT TOKEN (FROM @BOTFATHER)" else SystemConcepts.PHONE_IDENTIFIER.get(),
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
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            enabled = inputText.isNotBlank()
                        ) {
                            Text(if (isBotMode) "CONNECT BOT" else SystemConcepts.ESTABLISH_CONNECTION.get(), style = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                        }
                    }
                }

                TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                    var code by remember { mutableStateOf("") }
                    AuthLayout(
                        title = SystemConcepts.ACCESS_PROTOCOL.get(),
                        subtitle = SystemConcepts.VERIFY_ACCESS_CODE.get(),
                        onBack = { manager.logout() }
                    ) {
                        PremiumTextField(
                            value = code,
                            onValueChange = { code = it },
                            label = SystemConcepts.AUTH_CODE.get(),
                            keyboardType = KeyboardType.Number
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = { manager.sendVerificationCode(code) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            enabled = code.isNotBlank()
                        ) {
                            Text(SystemConcepts.AUTHORIZE.get(), style = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                        }
                    }
                }

                TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                    var password by remember { mutableStateOf("") }
                    AuthLayout(
                        title = SystemConcepts.DECRYPT_WORKSPACE.get(),
                        subtitle = SystemConcepts.ENTER_SYSTEM_KEY.get(),
                        onBack = { manager.logout() }
                    ) {
                        PremiumTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = SystemConcepts.SYSTEM_PASSWORD.get()
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        Button(
                            onClick = { manager.sendPassword(password) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(4.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            enabled = password.isNotBlank()
                        ) {
                            Text(SystemConcepts.DECRYPT.get(), style = TextStyle(fontWeight = FontWeight.Bold, letterSpacing = 1.sp))
                        }
                    }
                }

                else -> {
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(modifier = Modifier.height(32.dp))
                            Text(
                                SystemConcepts.SYSTEM_INITIALIZING.get(), 
                                style = TextStyle(fontFamily = JetBrainsMono, color = Color.White, fontSize = 10.sp, letterSpacing = 3.sp, fontWeight = FontWeight.ExtraLight)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun UserAvatar(user: TdApi.User, manager: TelegramManager, downloadedFiles: Map<Int, String>, size: Int) {
    val photoFile = user.profilePhoto?.small
    var imagePath: String? = null

    if (photoFile != null) {
        if (photoFile.local.isDownloadingCompleted) {
            imagePath = photoFile.local.path
        } else {
            imagePath = downloadedFiles[photoFile.id]
            if (imagePath == null) {
                manager.downloadFile(photoFile.id, 1)
            }
        }
    }

    Surface(
        modifier = Modifier.size(size.dp).clip(CircleShape),
        color = Color(0xFF222222),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        if (imagePath != null) {
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val initial = (user.firstName.take(1) + user.lastName.take(1)).uppercase()
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontSize = (size / 2.5).sp,
                    fontFamily = Inter
                )
            }
        }
    }
}