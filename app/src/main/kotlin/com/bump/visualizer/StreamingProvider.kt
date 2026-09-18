package com.bump.visualizer

enum class StreamingProvider(val label: String) {
    SPOTIFY("Spotify"),
    APPLE_MUSIC("Apple Music")
}

data class ProviderConnectionState(
    val provider: StreamingProvider,
    val connected: Boolean = false,
    val message: String = "Not connected"
)
