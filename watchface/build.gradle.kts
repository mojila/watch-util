plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.watchutil.watchface"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.watchutil.watchface"
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
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Wear Watch Face library. This module renders directly to a Canvas, so it
    // deliberately does not depend on Compose or Wear Compose.
    implementation("androidx.wear.watchface:watchface:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-data:1.2.1")
    implementation("androidx.wear.watchface:watchface-complications-rendering:1.2.1")

    implementation("androidx.core:core-ktx:1.16.0")

    testImplementation("junit:junit:4.13.2")
}
