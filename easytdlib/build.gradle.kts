import java.util.Properties

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.unknown.maragram.easytdlib"
    compileSdk = 36

    defaultConfig {
        minSdk = 24
        
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        
        ndk {
            abiFilters.add("arm64-v8a")
        }

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(localPropertiesFile.inputStream())
        }
        buildConfigField("String", "TURSO_URL", "\"${localProperties.getProperty("TURSO_URL") ?: ""}\"")
        buildConfigField("String", "TURSO_TOKEN", "\"${localProperties.getProperty("TURSO_TOKEN") ?: ""}\"")
        // Telegram API credentials stay in the untracked local.properties, never in the repository.
        buildConfigField("int", "TELEGRAM_API_ID", localProperties.getProperty("TELEGRAM_API_ID") ?: "0")
        buildConfigField("String", "TELEGRAM_API_HASH", "\"${localProperties.getProperty("TELEGRAM_API_HASH") ?: ""}\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    testImplementation(libs.junit)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    
    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.0")
    
    // Networking for Analytics
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}
