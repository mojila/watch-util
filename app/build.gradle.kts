plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.watchutil"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.watchutil"
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    // Wear Compose. 1.6.1 aligns with Compose UI 1.9.0 and is the newest line
    // that builds with AGP 8.13 / compileSdk 36. Wear Compose 1.7.0 requires
    // AGP 9.1+ and compileSdk 37.
    implementation("androidx.wear.compose:compose-material3:1.6.1")
    implementation("androidx.wear.compose:compose-foundation:1.6.1")

    implementation("androidx.compose.ui:ui:1.9.0")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling:1.9.0")

    testImplementation("junit:junit:4.13.2")
    // Real org.json for JVM unit tests; android.jar only ships stubs.
    testImplementation("org.json:json:20240303")
}
