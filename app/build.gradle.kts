import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val buildLabel = (findProperty("buildLabel") as String?) ?: run {
    val gitSha = try {
        providers.exec {
            commandLine("git", "rev-parse", "--short", "HEAD")
            workingDir = rootDir
        }.standardOutput.asText.get().trim()
    } catch (e: Exception) {
        "unknown"
    }
    val time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
    "$time $gitSha"
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun releaseSecret(name: String): String? =
    localProperties.getProperty("kapow.keystore.$name") ?: System.getenv("KAPOW_KEYSTORE_${name.uppercase()}")

val uploadKeystorePath = releaseSecret("path")

android {
    namespace = "com.comicify"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.sanchezpaco.kapow"
        minSdk = 29
        targetSdk = 37
        versionCode = 3
        versionName = "1.0.2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "BUILD_LABEL", "\"$buildLabel\"")

        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        if (uploadKeystorePath != null) {
            create("upload") {
                storeFile = file(uploadKeystorePath)
                storePassword = releaseSecret("storePassword")
                keyAlias = releaseSecret("keyAlias")
                keyPassword = releaseSecret("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        noCompress += "ort"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.play.review)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.androidx.window)
    implementation(libs.coil.compose)
    implementation(libs.androidx.palette)
    implementation(libs.sevenzip)
    implementation(files("libs/onnxruntime-minimal-1.20.0.aar"))

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
