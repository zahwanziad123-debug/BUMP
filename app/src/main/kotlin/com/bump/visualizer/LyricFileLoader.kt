package com.bump.visualizer

import android.content.Context
import android.net.Uri

object LyricFileLoader {
    fun load(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
}
