plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "de.codinix.videoeditor"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "de.codinix.videoeditor"
        minSdk = 26
        targetSdk = 35
        versionCode = 44
        versionName = "2.1-tile-videos"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // Fester Signaturschlüssel, damit jeder Build als Update über den vorherigen
    // installierbar ist. Ein neuer Zufallsschlüssel pro Build würde Android zur
    // Meldung "Paket in Konflikt mit bestehendem Paket" veranlassen.
    signingConfigs {
        create("fixed") {
            storeFile = rootProject.file("keystore/videoeditor.jks")
            storePassword = "videoeditor"
            keyAlias = "videoeditor"
            keyPassword = "videoeditor"
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fixed")
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
        viewBinding = true
        buildConfig = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    defaultConfig {
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
                cppFlags += listOf("-O3")
            }
        }
    }
}

dependencies {
    val camerax = "1.4.1"
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    val media3 = "1.5.1"
    implementation("androidx.media3:media3-transformer:$media3")
    implementation("androidx.media3:media3-effect:$media3")
    implementation("androidx.media3:media3-common:$media3")
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-ui:$media3")
}
