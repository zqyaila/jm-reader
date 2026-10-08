package com.jm.reader.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.jm.reader.desktop.ui.AppContainer
import com.jm.reader.desktop.ui.App

/**
 * Entry point for the Windows / macOS / Linux desktop build.
 *
 * `mainClass` in `desktop/build.gradle.kts` points at this file (`...MainKt`); the Windows
 * installer produced by jpackage launches exactly this.
 */
fun main() = application {
    // A reader wants width: the default window is sized for a two-column detail layout plus the
    // reading column, not for a phone-shaped viewport.
    val windowState = rememberWindowState(size = DpSize(1150.dp, 800.dp))
    val container = remember { AppContainer() }

    Window(
        onCloseRequest = ::exitApplication,
        state = windowState,
        title = "JM Reader",
    ) {
        App(container)
    }
}
