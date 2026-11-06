package com.unknown.maragram.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.unknown.maragram.core.SystemConcepts
import com.unknown.maragram.core.get
import com.unknown.maragram.engine.TelegramManager
import org.drinkless.tdlib.TdApi

@Composable
fun ChatListItem(chat: TdApi.Chat, manager: TelegramManager) {
    val downloadedFiles by manager.downloadedFiles.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val photoFile = chat.photo?.small
        var imagePath: String? = null

        if (photoFile != null) {
            if (photoFile.local.isDownloadingCompleted) {
                imagePath = photoFile.local.path
            } else {
                imagePath = downloadedFiles[photoFile.id]
                if (imagePath == null) {
                    manager.downloadFile(photoFile.id)
                }
            }
        }

        if (imagePath != null) {
            AsyncImage(
                model = imagePath,
                contentDescription = SystemConcepts.PROFILE_PICTURE.get(),
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Surface(
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = chat.title.take(1).uppercase(),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            
            Text(
                text = if (chat.lastMessage != null) SystemConcepts.MESSAGE_RECEIVED.get() else SystemConcepts.NO_MESSAGES.get(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
