import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

dependencyLocking {
    lockAllConfigurations()
}

// Public RevenueCat key lives in local.properties (gitignored), never as a literal in source. See
// docs/research/revenuecat.md §6 and android/local.properties.example for the key name.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val revenueCatApiKey: String = localProperties.getProperty("revenuecat.apiKey", "")
val releaseRevenueCatApiKey: String = localProperties.getProperty("revenuecat.releaseApiKey", "").ifBlank { revenueCatApiKey }
    .takeUnless { it.startsWith("test_") }.orEmpty()

android {
    namespace = "dev.handspell.app"
    // compileSdk/targetSdk 37, not 36: the pinned stable AndroidX releases (core-ktx 1.19.0,
    // Compose BOM 2026.09.00, lifecycle 2.11.0) all declare a compileSdk-37 floor in their AAR
    // metadata. Bumped as the minimal fix to make the build pass with the actually-current stable
    // dependency versions verified in gradle/libs.versions.toml (docs/CONTRACTS.md said 36).
    // AGP 9.4.0's documented max compileSdk is 37, and platforms;android-37.0 is installed
    // (docs/DEV_SETUP.md), so this is within the toolchain's supported range.
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.handspell.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "REVENUECAT_API_KEY", "\"$revenueCatApiKey\"")
    }

    buildTypes {
        release {
            buildConfigField("String", "REVENUECAT_API_KEY", "\"$releaseRevenueCatApiKey\"")
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
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
    }

    sourceSets.getByName("test").resources.srcDir("../../training/testdata")
    sourceSets.getByName("test").resources.srcDir("src/main/assets/classifier")

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    implementation(libs.mediapipe.tasks.vision)

    // purchases-ui is intentionally not used: the paywall is our own Compose screen, not
    // RevenueCat's dashboard-built one (docs/ARCHITECTURE.md §9, docs/CONTRACTS.md §5).
    implementation(libs.revenuecat.purchases)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.test.runner)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
