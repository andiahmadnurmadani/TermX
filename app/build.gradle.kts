plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// Versi: dari environment CI (tag vX.Y.Z) atau gradle.properties.
val termxVersionName: String = System.getenv("TERMX_VERSION_NAME")
    ?: (project.findProperty("termx.versionName") as String? ?: "0.0.0")
val termxVersionCode: Int = System.getenv("TERMX_VERSION_CODE")?.toIntOrNull()
    ?: (project.findProperty("termx.versionCode") as String?)?.toIntOrNull() ?: 1

android {
    namespace = "com.kroombox.termx"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.kroombox.termx"
        minSdk = 26
        targetSdk = 34
        versionCode = termxVersionCode
        versionName = termxVersionName
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        // Kunci rilis hanya ada di CI (secret GitHub), tidak pernah di repo.
        val releaseStore = System.getenv("TERMX_KEYSTORE_FILE")
        if (releaseStore != null && file(releaseStore).exists()) {
            create("release") {
                storeFile = file(releaseStore)
                storeType = "pkcs12"
                storePassword = System.getenv("TERMX_KEYSTORE_PASSWORD")
                keyAlias = "termx"
                keyPassword = System.getenv("TERMX_KEYSTORE_PASSWORD")
            }
        }
        // Kunci debug tetap (bukan rahasia, hanya untuk build debug) supaya APK dari CI bisa dipasang
        // menimpa versi sebelumnya tanpa uninstall — data host dan sesi tidak hilang.
        getByName("debug") {
            storeFile = file("termx-debug.p12")
            storeType = "pkcs12"
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Minify dimatikan: sshj + BouncyCastle memakai refleksi.
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
            excludes += setOf(
                "META-INF/versions/**",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/*.SF",
                "META-INF/*.DSA",
                "META-INF/*.RSA",
                "META-INF/BCKEY.*",
                "/META-INF/{AL2.0,LGPL2.1}",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-service:2.8.5")
    implementation("androidx.navigation:navigation-compose:2.8.0")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // SSH / SFTP / port forwarding
    implementation("com.hierynomus:sshj:0.39.0")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("org.slf4j:slf4j-nop:2.0.13")

    testImplementation("junit:junit:4.13.2")
}
