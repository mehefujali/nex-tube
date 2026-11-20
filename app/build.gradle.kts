import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.nextube.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nextube.app"
        minSdk = 26
        targetSdk = 36
        // CI passes -PversionCode / -PversionName when it builds a release.
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 10000
        versionName = (project.findProperty("versionName") as String?) ?: "1.0.0"

        // Where the app looks for new versions. Raw GitHub file, no server needed.
        val updateUrl = (project.findProperty("updateUrl") as String?)
            ?: "https://raw.githubusercontent.com/mehefujali/nex-tube/main/update.json"
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
    }

    // One APK per CPU type keeps each download small; the universal APK runs everywhere.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            isUniversalApk = true
        }
    }

    // Release signing comes from keystore.properties (local) or NEXTUBE_* env vars (CI).
    // Without either, release builds fall back to the debug key so anyone can build and install.
    val keystoreProps = Properties().apply {
        val f = rootProject.file("keystore.properties")
        if (f.exists()) f.inputStream().use { load(it) }
    }
    fun signingValue(key: String, env: String): String? =
        keystoreProps.getProperty(key) ?: System.getenv(env)?.takeIf { it.isNotBlank() }
    val storeFilePath = signingValue("storeFile", "NEXTUBE_KEYSTORE_FILE")

    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = signingValue("storePassword", "NEXTUBE_KEYSTORE_PASSWORD")
                keyAlias = signingValue("keyAlias", "NEXTUBE_KEY_ALIAS")
                keyPassword = signingValue("keyPassword", "NEXTUBE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
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
    packaging {
        // youtubedl-android ships Python/ffmpeg as .so files that must be extracted to disk.
        jniLibs.useLegacyPackaging = true
        // These "libs" are zip archives in disguise; stripping them only produces warnings.
        jniLibs.keepDebugSymbols += setOf("**/libpython.zip.so", "**/libffmpeg.zip.so")
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "META-INF/DEPENDENCIES")
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    val ytdl = "0.18.1"
    implementation("io.github.junkfood02.youtubedl-android:library:$ytdl")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:$ytdl")

    val composeBom = platform("androidx.compose:compose-bom:2025.11.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")

    implementation("io.coil-kt.coil3:coil-compose:3.3.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")

    val media3 = "1.8.0"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
}
