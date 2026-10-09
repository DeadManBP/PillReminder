plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "ca.pillreminder"
    compileSdk = 35

    defaultConfig {
        applicationId = "ca.pillreminder"
        minSdk = 26
        targetSdk = 35
        versionCode = (project.findProperty("pillVersionCode")?.toString()?.toIntOrNull() ?: 1)
        versionName = (project.findProperty("pillVersionName")?.toString() ?: "1.0.0")
    }

    signingConfigs {
        create("pill") {
            val ksPath = System.getenv("PILL_KEYSTORE_FILE")
            if (!ksPath.isNullOrBlank()) {
                storeFile = file(ksPath)
                storeType = "PKCS12"
                storePassword = System.getenv("PILL_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PILL_KEY_ALIAS") ?: "brockvillehub"
                keyPassword = System.getenv("PILL_KEY_PASSWORD") ?: System.getenv("PILL_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            if (!System.getenv("PILL_KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("pill")
            }
        }
        release {
            if (!System.getenv("PILL_KEYSTORE_FILE").isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("pill")
            }
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
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
}
