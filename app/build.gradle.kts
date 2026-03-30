plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    id("org.jetbrains.kotlin.kapt")
}

import java.util.Properties

fun firstNonBlank(vararg values: String?): String? = values.firstOrNull { !it.isNullOrBlank() }?.trim()

val releaseSigningProperties = Properties().apply {
    val localSigningFile = rootProject.file("release-signing.properties")
    if (localSigningFile.exists()) {
        localSigningFile.inputStream().use(::load)
    }
}

val debugImportRelayApiBaseUrl = (project.findProperty("wearpodImportRelayApiBaseUrlDebug") as String?)
    ?: (project.findProperty("wearpodImportRelayApiBaseUrl") as String?)
    ?: "http://10.0.2.2:8787"
val debugImportRelayFallbackApiBaseUrl = (project.findProperty("wearpodImportRelayFallbackApiBaseUrlDebug") as String?)
    ?: (project.findProperty("wearpodImportRelayFallbackApiBaseUrl") as String?)
    ?: ""
val releaseImportRelayApiBaseUrl = (project.findProperty("wearpodImportRelayApiBaseUrlRelease") as String?)
    ?: (project.findProperty("wearpodImportRelayApiBaseUrl") as String?)
    ?: ""
val releaseImportRelayFallbackApiBaseUrl = (project.findProperty("wearpodImportRelayFallbackApiBaseUrlRelease") as String?)
    ?: (project.findProperty("wearpodImportRelayFallbackApiBaseUrl") as String?)
    ?: ""
val releaseStoreFilePath = firstNonBlank(
    project.findProperty("WEARPOD_RELEASE_STORE_FILE") as String?,
    releaseSigningProperties.getProperty("WEARPOD_RELEASE_STORE_FILE"),
    System.getenv("WEARPOD_RELEASE_STORE_FILE"),
)
val releaseStorePassword = firstNonBlank(
    project.findProperty("WEARPOD_RELEASE_STORE_PASSWORD") as String?,
    releaseSigningProperties.getProperty("WEARPOD_RELEASE_STORE_PASSWORD"),
    System.getenv("WEARPOD_RELEASE_STORE_PASSWORD"),
)
val releaseKeyAlias = firstNonBlank(
    project.findProperty("WEARPOD_RELEASE_KEY_ALIAS") as String?,
    releaseSigningProperties.getProperty("WEARPOD_RELEASE_KEY_ALIAS"),
    System.getenv("WEARPOD_RELEASE_KEY_ALIAS"),
)
val releaseKeyPassword = firstNonBlank(
    project.findProperty("WEARPOD_RELEASE_KEY_PASSWORD") as String?,
    releaseSigningProperties.getProperty("WEARPOD_RELEASE_KEY_PASSWORD"),
    System.getenv("WEARPOD_RELEASE_KEY_PASSWORD"),
)
val hasReleaseSigning = listOf(
    releaseStoreFilePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { !it.isNullOrBlank() }

android {
    namespace = "com.sjtech.wearpod"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sjtech.wearpod"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(checkNotNull(releaseStoreFilePath))
                storePassword = checkNotNull(releaseStorePassword)
                keyAlias = checkNotNull(releaseKeyAlias)
                keyPassword = checkNotNull(releaseKeyPassword)
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField(
                "String",
                "IMPORT_RELAY_API_BASE_URL",
                "\"$debugImportRelayApiBaseUrl\"",
            )
            buildConfigField(
                "String",
                "IMPORT_RELAY_FALLBACK_API_BASE_URL",
                "\"$debugImportRelayFallbackApiBaseUrl\"",
            )
        }
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField(
                "String",
                "IMPORT_RELAY_API_BASE_URL",
                "\"$releaseImportRelayApiBaseUrl\"",
            )
            buildConfigField(
                "String",
                "IMPORT_RELAY_FALLBACK_API_BASE_URL",
                "\"$releaseImportRelayFallbackApiBaseUrl\"",
            )
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.media3.common)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.zxing.core)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
