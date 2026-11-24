import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Release signing comes from signing/keystore.properties (gitignored). Without it, release builds are unsigned.
val signingProps = Properties().apply {
    val f = rootProject.file("signing/keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

// Update source: the GitHub repo ("owner/name") whose Releases hold the APKs. Set in gradle.properties.
val githubRepo = (findProperty("sitesnap.githubRepo") as String?).orEmpty()
val updateApiBase = (findProperty("sitesnap.updateApiBase") as String?) ?: "https://api.github.com"

android {
    namespace = "com.sitesnap.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sitesnap.app"
        // API 29+ gives us MediaStore RELATIVE_PATH + scoped storage, so no storage permission is needed to write.
        minSdk = 29
        targetSdk = 36
        // Bump both for every GitHub release. versionName must match the release tag (v1.0.0 -> 1.0.0).
        versionCode = 5
        versionName = "1.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")
        buildConfigField("String", "UPDATE_API_BASE", "\"$updateApiBase\"")
        // Plain http is only allowed when testing updates against a local server.
        manifestPlaceholders["usesCleartext"] = updateApiBase.startsWith("http://").toString()
    }

    signingConfigs {
        if (signingProps.containsKey("storeFile")) {
            create("release") {
                storeFile = rootProject.file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.navigation:navigation-compose:2.8.5")

    // Camera
    val camerax = "1.4.1"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    // Background sync
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // Google Drive: Google Identity authorization + Drive REST v3 over OkHttp
    implementation("com.google.android.gms:play-services-auth:21.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Thumbnails from MediaStore content:// URIs
    implementation("io.coil-kt:coil-compose:2.7.0")

    // On-device UI tests (two-finger pinch etc.): ./gradlew connectedDebugAndroidTest
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
