plugins {
    id("finiteplay.shared-layer")
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "org.finiteplay.core.ui"

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

// Depends on :core:cards and nothing game-shaped: card rendering needs to know what a
// card *is*, but nothing here may know what Klondike is (`docs/ARCHITECTURE.md`, and the
// assertNoGameReferences check enforces it).
dependencies {
    api(project(":core:cards"))
    // For SYSTEM_LANGUAGE, the persisted value the language picker offers. core:storage depends
    // on no other project, so this direction cannot cycle.
    api(project(":core:storage"))
    // For formatElapsed, so a dialog naming a game's elapsed time reads it the same way the
    // status row does. core:session depends on no other project, so this cannot cycle.
    api(project(":core:session"))

    val composeBom = platform(libs.compose.bom)
    api(composeBom)

    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.ui)
    api(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)

    testImplementation(libs.junit4)
}
