plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.blesense.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blesense.app"
        minSdk = 29
        targetSdk = 36
        versionCode = 22  // Increment version for Play Store update
        versionName = "1.2.3"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseStorePassword = project.findProperty("RELEASE_STORE_PASSWORD") as String?
    val releaseKeyPassword = project.findProperty("RELEASE_KEY_PASSWORD") as String?

    if (!releaseStorePassword.isNullOrBlank()) {
        signingConfigs {
            create("release") {
                storeFile = file("release/ble-release-key")
                storePassword = releaseStorePassword
                keyAlias = project.findProperty("RELEASE_KEY_ALIAS") as String? ?: "release....v2.0"
                keyPassword = releaseKeyPassword ?: releaseStorePassword
            }
        }
    }

    buildTypes {
        release {
            if (!releaseStorePassword.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true  // Enable for smaller APK
            isShrinkResources = true  // Remove unused resources
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
        mlModelBinding = false  // DISABLED - Not using ML models
    }

    // Fix for 16KB page size issue
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    // ==================== CORE DEPENDENCIES ====================
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))

    // ==================== COROUTINES ====================
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ==================== COMPOSE UI (REQUIRED) ====================
    implementation("androidx.compose.ui:ui:1.5.4")
    implementation("androidx.compose.ui:ui-tooling-preview:1.5.4")
    implementation("androidx.compose.foundation:foundation:1.5.1")
    implementation("androidx.compose.runtime:runtime:1.6.8")
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)

    // ==================== MATERIAL DESIGN ====================
    implementation("androidx.compose.material:material:1.5.4")
    implementation("androidx.compose.material3:material3:1.1.0")
    implementation(libs.androidx.material3)
    implementation("com.google.android.material:material:1.9.0")

    // ==================== MATERIAL ICONS ====================
    implementation("androidx.compose.material:material-icons-core:1.5.1")
    implementation("androidx.compose.material:material-icons-extended:1.6.8")

    // ==================== NETWORKING (Optional - only if you need it) ====================
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("org.json:json:20210307")

    // ==================== NAVIGATION ====================
    implementation("androidx.navigation:navigation-compose:2.7.0")
    implementation("com.google.accompanist:accompanist-navigation-animation:0.30.1")


    // ==================== WORKMANAGER ====================
    implementation(libs.androidx.work.runtime.ktx)
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ==================== OTHER UTILITIES ====================
    implementation("androidx.core:core-splashscreen:1.0.0")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.9.10")
    implementation("com.google.accompanist:accompanist-permissions:0.32.0")
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.36.0")
    implementation("org.jetbrains:annotations:23.0.0")

    // ==================== LIFECYCLE ====================
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation(libs.foundation)

    // ==================== NORDIC SEMICONDUCTOR ====================
    implementation("no.nordicsemi.android.kotlin.ble:scanner:1.3.1")
    implementation("no.nordicsemi.android.kotlin.ble:advertiser:1.3.1")
    implementation("no.nordicsemi.android.kotlin.ble:core:1.3.1")

    // ==================== TESTING ====================
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // ==================== DEBUG ====================
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    debugImplementation("androidx.compose.ui:ui-tooling:1.5.4")
}

configurations.all {
    exclude(group = "com.intellij", module = "annotations")
}