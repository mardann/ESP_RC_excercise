package com.procyon.esp_rc.ui

import androidx.compose.ui.graphics.Color

enum class ConnectionsState(val color: Color, val text: String, val flashing: Boolean) {
    Scanning(Color(0xFFFF9422), "Scanning...", true),
    Connecting(Color.Green, "Connecting...", true),
    Connected(Color.Green, "Connected", false),
    Disconnected(Color.Red, "Disconnected", false),
    Error(Color.Red, "Disconnected", true)
}