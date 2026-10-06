import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Firebase AI Logic is the primary LLM (SRS 4.3). It is only wired in when the Firebase
// config file exists, so the app still builds and runs (with Groq or offline) without it.
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// API keys not managed by Firebase live in secrets.properties, which is git-ignored (NFR-7).
val secrets = Properties().apply {
    rootProject.file("secrets.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
fun secret(name: String) = "\"" + secrets.getProperty(name, "").replace("\"", "") + "\""

android {
    namespace = "com.ayush.baymax"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ayush.baymax"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "GROQ_API_KEY", secret("GROQ_API_KEY"))
        buildConfigField("String", "GEMINI_API_KEY", secret("GEMINI_API_KEY"))
    }

    buildTypes {
        release {
            // Personal, unpublished app (C5): no shrinking, so reflection-based libraries just work.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    debugImplementation(libs.androidx.ui.tooling)

    // Data (SRS 7)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)

    // Tools
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.health.connect)

    // LLM
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.ai)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
