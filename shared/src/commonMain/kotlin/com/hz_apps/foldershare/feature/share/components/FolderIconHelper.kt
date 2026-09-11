package com.hz_apps.foldershare.feature.share.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Returns an appropriate Material vector icon based on folder name or emoji mapping.
 */
fun getFolderVectorIcon(name: String, iconEmoji: String? = null): ImageVector {
    val lowercaseName = name.lowercase()
    return when {
        lowercaseName.contains("download") || iconEmoji == "📥" -> Icons.Default.Download
        lowercaseName.contains("doc") || iconEmoji == "📄" -> Icons.Default.Description
        lowercaseName.contains("music") || lowercaseName.contains("audio") || iconEmoji == "🎵" -> Icons.Default.MusicNote
        lowercaseName.contains("video") || lowercaseName.contains("movie") || iconEmoji == "🎬" -> Icons.Default.Movie
        lowercaseName.contains("picture") || lowercaseName.contains("image") || lowercaseName.contains("photo") || lowercaseName.contains("dcim") || iconEmoji == "🖼️" -> Icons.Default.PhotoLibrary
        else -> Icons.Default.FolderShared
    }
}
