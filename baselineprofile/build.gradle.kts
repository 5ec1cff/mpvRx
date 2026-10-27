import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  alias(libs.plugins.android.test)
  alias(libs.plugins.baselineprofile)
}

android {
  namespace = "app.gyrolet.mpvrx.baselineprofile"
  compileSdk = 37

  defaultConfig {
    minSdk = 33
    targetSdk = 36
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true
  flavorDimensions += "distribution"
  productFlavors {
    listOf("standard", "noVulkan", "fongmi", "playstore").forEach { flavor ->
      create(flavor) { dimension = "distribution" }
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

baselineProfile { useConnectedDevices = true }

dependencies {
  implementation(libs.androidx.benchmark.macro.junit4)
  implementation(libs.androidx.test.runner)
  implementation(libs.androidx.test.ext.junit)
}
