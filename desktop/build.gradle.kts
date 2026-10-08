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

// NOTE: no `repositories { }` block here on purpose.
//
// The root `settings.gradle.kts` sets `dependencyResolutionManagement { repositoriesMode =
// FAIL_ON_PROJECT_REPOS }`, so a module that declares its own repositories makes Gradle fail
// configuration outright:
//
//   "Build was configured to prefer settings repositories over project repositories but
//    repository 'Google' was added by build file 'desktop/build.gradle.kts'"
//
// `google()` and `mavenCentral()` are already declared centrally there and are inherited by this
// module. Add any new repository to `dependencyResolutionManagement` in settings.gradle.kts
// instead of re-adding this block.

dependencies {
    // Compose Multiplatform for Desktop, resolved for the OS the build runs on (Windows in CI ->
    // the windows-x64 Skiko binary).
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Material icons: declared explicitly, for two reasons.
    //
    //  1. Compose Multiplatform 1.8.2 dropped the implicit transitive dependency on
    //     `material-icons-core` ("Implicit dependency on material-icons-core removed" in the
    //     1.8.2 release notes), so `Icons.Filled.Search` / `Home` / `AutoMirrored.ArrowBack`
    //     would no longer resolve on their own.
    //  2. `material-icons-extended` is pinned upstream at 1.7.3 and will not receive further
    //     releases, so the `compose.materialIconsExtended` Gradle accessor is deprecated (and is
    //     gone in newer plugin lines). Spelling out the coordinate is version-proof.
    //
    // `extended` is only needed for a few glyphs outside the core set (`AutoStories`,
    // `NavigateBefore/Next`, `ContentCopy`); it is kept because the alternative is regressing the
    // design to whatever the ~50 core icons happen to offer.
    implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")
    implementation("org.jetbrains.compose.material:material-icons-extended:1.7.3")

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

            // -------------------------------------------------------------------------------
            // These five fields must stay ASCII. That is not cosmetic: it is the difference
            // between a green and a red "Package MSI and EXE" step.
            //
            // jpackage writes them straight into the WiX sources it generates, and WiX's linker
            // (light.exe) stores them in the MSI string tables using the code page of the
            // installer's culture. jpackage hard-codes `-cultures:en-us` on Windows, i.e. code
            // page 1252, and there is no Compose DSL knob to change it - doing so would mean
            // overriding jpackage's own main.wxs. Any character outside cp1252 (CJK, full-width
            // punctuation) makes light.exe abort with
            //
            //   error LGHT0311 : A string was provided with characters that are not available
            //   in the specified database code page '1252'.
            //
            // which jpackage reports only as "External tool execution failed ... Exit code: 311",
            // a long way away from the actual cause. The em dash and the copyright sign are in
            // fact representable in cp1252 and were never the problem - the Chinese text was. The
            // tripwire below is nevertheless stricter than cp1252 and demands plain ASCII: "stay
            // inside 0x20..0x7E" is a rule that is trivial to check and needs no code page table,
            // and these fields never needed anything fancier.
            //
            // The localised product copy belongs in the app itself (the About screen), where a
            // human actually reads it. These fields land in the MSI's Package/Comments property,
            // which is invisible to the end user: Add/Remove Programs shows ProductName
            // ("JMReader", from packageName) and Publisher (vendor), both already ASCII.
            // -------------------------------------------------------------------------------
            description = "JM Reader - unofficial third-party comic reader (cross-platform)"
            vendor = "JMReader Contributors"
            copyright = "Copyright (c) 2026 JMReader Contributors - GPL-3.0"

            // Fail at *configuration* time - seconds into the run, with the message below - rather
            // than 40+ seconds later inside a jpackage subprocess that only says "Exit code: 311".
            // This exact bug cost several CI round trips, so it gets a tripwire.
            listOf(
                "packageName" to packageName,
                "packageVersion" to packageVersion,
                "description" to description,
                "vendor" to vendor,
                "copyright" to copyright,
            ).forEach { (field, value) ->
                val offending = value?.filter { it.code > 0x7E || it.code < 0x20 }.orEmpty()
                check(offending.isEmpty()) {
                    "jpackage field `$field` = \"$value\" contains characters WiX cannot encode " +
                        "in code page 1252 ($offending). Keep this field ASCII - see the note above."
                }
            }

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
