import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Version is derived from a single number. CI passes `-PbuildNumber=<run_number>`, so every
// published APK carries a real, increasing version; a local build keeps the defaults.
//
// A bare integer rather than `-PversionName=1.0.12` on purpose: that dotted value is re-parsed by
// the desktop module's jpackage and arrived as `1` on the Windows runner, which failed the whole
// build with "Illegal version for 'Msi': '1'". One plain number cannot be misread.
//
// `versionCode` is offset by 100 so CI codes stay above the local development builds that were
// already installed by hand (installing a lower code over them fails with VERSION_DOWNGRADE).
val buildNumber = providers.gradleProperty("buildNumber").orNull?.toIntOrNull() ?: 0
val appVersionCode = if (buildNumber > 0) 100 + buildNumber else 1
val appVersionName = if (buildNumber > 0) "1.0.$buildNumber" else "1.0.0"

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.jm.reader"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jm.reader"
        minSdk = 24
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        // Compile time, used by the in-app updater to decide whether the rolling GitHub release is
        // newer than the build this APK came from. The release is recreated on every green build,
        // so its `published_at` is comparable to this timestamp.
        buildConfigField("long", "BUILD_TIME", System.currentTimeMillis().toString() + "L")
    }

    signingConfigs {
        create("release") {
            // Signing material is never committed (`*.keystore` is git-ignored). Locally it lives
            // at the project root; in CI it is restored from secrets before `assembleRelease`.
            // Credentials come from the environment so a real key does not have to be written into
            // this file; the literals below are only the previous defaults, kept so an existing
            // local setup keeps working unchanged.
            val keystore = rootProject.file("jmreader.keystore")
            if (keystore.exists()) {
                storeFile = keystore
                storePassword = System.getenv("JMREADER_STORE_PASSWORD") ?: "jmreader123"
                keyAlias = System.getenv("JMREADER_KEY_ALIAS") ?: "jmreader"
                keyPassword = System.getenv("JMREADER_KEY_PASSWORD") ?: "jmreader123"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Attach signing only when the keystore is actually present. Referencing a missing
            // store file makes `assembleRelease` fail outright, which would break a fresh clone and
            // any CI run without the signing secrets; an unsigned release APK is still a valid
            // build artifact, so the build degrades instead of dying.
            if (rootProject.file("jmreader.keystore").exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // AndroidX
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    // LifecycleResumeEffect: used by the reader to count only the time the screen is actually
    // resumed, so a backgrounded reader does not inflate the local reading stats.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.4")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Image loading
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Unit tests run on the JVM: the real org.json implementation substitutes for the
    // android.jar stub so the response/record parsers can be exercised with captured payloads.
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
