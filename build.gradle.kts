// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("com.android.application") version "8.13.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.0" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.0" apply false
    // Desktop targets (:desktop). Kotlin JVM + Compose Multiplatform for Desktop.
    // Compose Multiplatform 1.8.2 is the release that pairs with the Kotlin 2.2.0 /
    // Compose 1.8 line this project already uses, so the ported UI compiles against the
    // same Material 3 APIs the Android screens do.
    id("org.jetbrains.kotlin.jvm") version "2.2.0" apply false
    id("org.jetbrains.compose") version "1.8.2" apply false
}
