import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

group = "com.jm.reader"
version = "1.0.0"

kotlin {
    // jpackage (used for the .exe / .msi installers) refuses to run on anything older than 17.
    jvmToolchain(17)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

repositories {
    // `google()` and `mavenCentral()` already come from the root project's
    // `dependencyResolutionManagement`, but the desktop module also resolves the Compose
    // Multiplatform plugin marker, so repeat them here for clarity when built standalone.
    google()
    mavenCentral()
}

dependencies {
    // Compose Multiplatform for Desktop: Material 3 + the extended icon set, resolved for the
    // OS the build runs on (Windows in CI -> the windows-x64 Skiko binary).
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Compose Desktop needs a `Dispatchers.Main`; the Swing module supplies the AWT/EDT one.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // Networking: same OkHttp major version the Android module uses, so the request signing and
    // retry behaviour port one-for-one.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // The real org.json implementation (the Android module gets it from the platform).
    implementation("org.json:json:20240303")

    testImplementation(kotlin("test"))
    testImplementation("org.json:json:20240303")
}

// ---------------------------------------------------------------------------
// Windows installer (jpackage)
// ---------------------------------------------------------------------------
//
// Two formats are declared because they serve different audiences:
//   * Msi — a proper Windows Installer package: installs into Program Files (per-machine) or
//     %LOCALAPPDATA% (per-user), registers an uninstaller, and supports in-place upgrades
//     through `upgradeUuid`.
//   * Exe — the same thing wrapped in a self-contained setup wizard, which is friendlier for
//     handing to someone who just wants to double-click and be done.
//
// Both are produced with the JDK's own `jpackage`, so no extra toolchain is needed on the
// developer machine beyond WiX (which jpackage requires on Windows and CI installs).
compose.desktop {
    application {
        mainClass = "com.jm.reader.desktop.MainKt"

        // Passed through to the app as system properties; the About screen reads them.
        jvmArgs += listOf("-Xmx2g")

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)

            packageName = "JMReader"
            packageVersion = "1.0.0"
            description = "JM Reader — 跨平台漫画阅读器（非官方第三方客户端）"
            vendor = "JMReader Contributors"
            copyright = "Copyright © 2026 — GPL-3.0"

            // Bundled runtime. `includeAllModules = true` ships the whole JDK runtime image, so
            // the target machine needs no Java at all.
            //
            // This is deliberately NOT trimmed. Compose Desktop, Skiko, OkHttp and `java.util.prefs`
            // between them touch more JDK modules than is obvious (AWT/Swing, JUL, EC crypto,
            // `jdk.unsupported` for Unsafe), and a missing module only shows up as a runtime crash
            // on the user's machine — not at build time. Trimming via `modules(...)` is a valid
            // follow-up once the packaged app has been smoke-tested on Windows; it roughly halves
            // the installer size, and `SCOPE_AND_ACCEPTANCE.md` lists it as such.
            includeAllModules = true

            windows {
                // Stable UUID: jpackage uses it to recognise "an older version of me" and
                // upgrade in place instead of installing a second copy side by side.
                // Never change this value once a release has shipped.
                upgradeUuid = "62e3cb66-7b23-4ad7-bb33-7ca81e4210ee"

                // Start-menu entry + optional desktop shortcut, and let the user pick a folder.
                menu = true
                shortcut = true
                dirChooser = true
                // Install under the current user by default: no admin prompt on a normal PC.
                perUserInstall = true

                iconFile.set(project.file("icons/jmreader.ico"))
            }
        }
    }
}
