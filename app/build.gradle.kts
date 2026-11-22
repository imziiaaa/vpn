import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use(localProperties::load)
}

val uploadInputs = listOf(
    "RAVEN_UPLOAD_STORE_FILE",
    "RAVEN_UPLOAD_STORE_PASSWORD",
    "RAVEN_UPLOAD_KEY_ALIAS",
    "RAVEN_UPLOAD_KEY_PASSWORD"
).associateWith { name ->
    providers.environmentVariable(name).orNull?.takeIf(String::isNotBlank)
        ?: localProperties.getProperty(name)?.takeIf(String::isNotBlank)
}
val missingUploadInputs = uploadInputs.filterValues { it == null }.keys
val hasUploadKey = missingUploadInputs.isEmpty()
if (!hasUploadKey && missingUploadInputs.size < uploadInputs.size) {
    logger.warn("Release artifacts will be unsigned. Missing Play upload signing inputs: ${missingUploadInputs.joinToString()}")
}

android {
    namespace = "com.veilgate.vpn"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.veilgate.vpn"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasUploadKey) {
            create("playUpload") {
                storeFile = rootProject.file(uploadInputs.getValue("RAVEN_UPLOAD_STORE_FILE")!!)
                storePassword = uploadInputs.getValue("RAVEN_UPLOAD_STORE_PASSWORD")
                keyAlias = uploadInputs.getValue("RAVEN_UPLOAD_KEY_ALIAS")
                keyPassword = uploadInputs.getValue("RAVEN_UPLOAD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("playUpload")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.security.crypto)

    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
