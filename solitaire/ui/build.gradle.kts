plugins {
    id("finiteplay.shared-layer")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "org.finiteplay.solitaire.ui"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Depends on Dp arithmetic alone (`compose.ui`'s `ui-unit`), and nothing game-shaped: a
// tableau's width- and overlap-fitting math needs to know what a Dp is, but nothing here
// may know what Klondike or Spider is (`docs/ARCHITECTURE.md`).
dependencies {
    val composeBom = platform(libs.compose.bom)
    api(composeBom)
    api(libs.compose.ui)

    testImplementation(libs.junit4)
}
