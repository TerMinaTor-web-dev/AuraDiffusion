import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.auradiffusion.app"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.auradiffusion.app"
    minSdk = 26
    targetSdk = 36
    versionCode = 3
    versionName = "1.0.2"

    ndk {
      abiFilters.add("arm64-v8a")
    }
  }

  signingConfigs {
    create("release") {
      val keystoreFile = rootProject.file("keystore/eva-release.keystore")
      if (keystoreFile.exists()) {
        storeFile = keystoreFile
        storePassword = "evasuperapp2026"
        keyAlias = "eva"
        keyPassword = "evasuperapp2026"
        enableV1Signing = true
        enableV2Signing = true
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      val keystoreFile = rootProject.file("keystore/eva-release.keystore")
      if (keystoreFile.exists()) {
        signingConfig = signingConfigs.getByName("release")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    debug {
      val keystoreFile = rootProject.file("keystore/eva-release.keystore")
      if (keystoreFile.exists()) {
        signingConfig = signingConfigs.getByName("release")
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    buildConfig = true
  }

  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    jniLibs {
      useLegacyPackaging = true
    }
  }
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.jna)
  implementation(libs.coil.compose)
}
