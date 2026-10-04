plugins {
    id("finiteplay.shared-layer")
    alias(libs.plugins.android.library)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "org.finiteplay.core.storage"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 26
    }

    buildFeatures {
        buildConfig = false
    }

    // Publishes FakeDataStores to every game's unit tests, so the DataStore substitute the
    // Windows rename bug forces is written once rather than per game.
    testFixtures {
        enable = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

// DataStore plumbing, the locale override, and the active-game record store.
dependencies {
    api(libs.datastore.preferences)
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    testFixturesImplementation(libs.datastore.preferences)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
