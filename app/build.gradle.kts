plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "tw.local.lineai"
    compileSdk = 35
    defaultConfig {
        applicationId = "tw.local.lineai"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }
    signingConfigs {
        create("fixed") {
            storeFile = file("lineai.keystore")
            storePassword = "timeline123"; keyAlias = "timeline"; keyPassword = "timeline123"
        }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("fixed") }
        debug { signingConfig = signingConfigs.getByName("fixed") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    lint { abortOnError = false; checkReleaseBuilds = false }
}
dependencies {}
