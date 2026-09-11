package com.hz_apps.foldershare.feature.explorer.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.hz_apps.foldershare.core.explorer.model.RemoteFile

@Composable
fun getFileIcon(file: RemoteFile): ImageVector {
    if (file.isDirectory) return Icons.Default.Folder

    return when (file.extension.lowercase()) {
        "png", "jpg", "jpeg", "webp", "gif", "svg", "bmp" -> Icons.Default.Image
        "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv" -> Icons.Default.Movie
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma" -> Icons.Default.MusicNote
        "zip", "tar", "gz", "rar", "7z", "iso" -> Icons.Default.Archive
        "kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "c", "cpp", "rs", "go", "sh" -> Icons.Default.Code
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }
}

@Composable
fun getFileIconTint(file: RemoteFile): Color {
    if (file.isDirectory) return MaterialTheme.colorScheme.primary

    return when (file.extension.lowercase()) {
        "png", "jpg", "jpeg", "webp", "gif", "svg", "bmp" -> MaterialTheme.colorScheme.secondary
        "mp4", "mkv", "avi", "mov", "webm", "flv", "wmv" -> MaterialTheme.colorScheme.tertiary
        "mp3", "wav", "flac", "aac", "ogg", "m4a", "wma" -> MaterialTheme.colorScheme.secondary
        "zip", "tar", "gz", "rar", "7z", "iso" -> MaterialTheme.colorScheme.tertiary
        "kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "c", "cpp", "rs", "go", "sh" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}
