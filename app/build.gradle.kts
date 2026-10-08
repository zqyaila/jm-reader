import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
        versionCode = 1
        versionName = "1.0.0"
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
