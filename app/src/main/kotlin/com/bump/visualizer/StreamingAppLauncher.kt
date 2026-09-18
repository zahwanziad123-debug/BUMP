package com.bump.visualizer

import android.content.Context
import android.content.Intent
import android.net.Uri

object StreamingAppLauncher {
    fun open(context: Context, provider: StreamingProvider): Boolean {
        val packageName = when (provider) {
            StreamingProvider.SPOTIFY -> "com.spotify.music"
            StreamingProvider.APPLE_MUSIC -> "com.apple.android.music"
        }

        val launch = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launch != null) {
            context.startActivity(launch)
            return true
        }

        val marketUri = Uri.parse("market://details?id=$packageName")
        return try {
            context.startActivity(Intent(Intent.ACTION_VIEW, marketUri))
            true
        } catch (_: Exception) {
            false
        }
    }
}
