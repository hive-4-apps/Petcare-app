import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Permanent signing key, given by CI from the repository secrets (or by a local keystore.properties, never committed).
// Same key for every build = Android installs updates over the previous version and keeps the app data.
val localSigning = rootProject.file("keystore.properties").takeIf { it.exists() }?.let { file ->
    Properties().apply { file.inputStream().use { load(it) } }
}
val petcareKeystore = (System.getenv("PETCARE_KEYSTORE_FILE") ?: localSigning?.getProperty("storeFile"))
    ?.let { rootProject.file(it) }?.takeIf { it.exists() }
val petcarePassword = System.getenv("PETCARE_KEYSTORE_PASSWORD") ?: localSigning?.getProperty("password")

android {
    namespace = "com.hive4apps.petcare"
    compileSdk = 35

    signingConfigs {
        if (petcareKeystore != null) {
            create("petcare") {
                storeFile = petcareKeystore
                storePassword = petcarePassword
                keyAlias = "petcare"
                keyPassword = petcarePassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "com.hive4apps.petcare"
        minSdk = 26
        targetSdk = 35
        // Always increasing, so each CI build can be installed over the previous one.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull()?.plus(100) ?: 1
        // Shown in Settings, to check which build is installed.
        versionName = "1.0.$versionCode"
        buildConfigField("String", "GIT_SHA", "\"${System.getenv("GITHUB_SHA")?.take(7) ?: "local"}\"")
        // Phones only: keeps the on-device text recognition small.
        ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a") }
    }

    buildTypes {
        debug {
            // Installs next to the release build, handy to test on the phone.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            if (petcareKeystore != null) signingConfig = signingConfigs.getByName("petcare")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (petcareKeystore != null) "petcare" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Reads the vet's documents on the phone (offline, nothing is sent anywhere).
    implementation("com.google.mlkit:text-recognition:16.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Launches the app on the JVM and goes through a whole follow-up (no phone needed).
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
