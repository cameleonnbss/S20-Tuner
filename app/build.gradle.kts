plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cameleonnbss.s20tuner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cameleonnbss.s20tuner"
        minSdk = 30            // Android 11+: Samsung S20 shipped on 10 but custom ROMs are 11+
        targetSdk = 36
        versionCode = 7
        versionName = "3.3"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.18.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.compose.ui:ui:1.10.6")
    implementation("androidx.compose.ui:ui-graphics:1.10.6")
    implementation("androidx.compose.ui:ui-tooling-preview:1.10.6")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.foundation:foundation:1.10.6")
    implementation("androidx.compose.animation:animation:1.10.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
