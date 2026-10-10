import org.finiteplay.buildlogic.AssertExcludedProjectTask
import java.util.Properties

// Workaround: allow running with older Gradle by suppressing AGP Gradle version check.
// This sets the system property before the Android plugin is applied.
System.setProperty("android.suppressUnsupportedGradleVersionCheck", "true")

// Release signing and Play publishing, read from the root `local.properties` (gitignored,
// never committed) rather than from anything checked in:
//
// - `finiteplay.upload.keystore` — absolute path to the shared FinitePlay upload keystore.
// - `finiteplay.upload.keystorePassword` — its password, also every alias's key password
//   (the keystore is PKCS12, which does not support a different key password per alias).
// - `finiteplay.play.serviceAccountJson` — absolute path to the Play Console API service
//   account key. Only `publishBundle`/`promoteArtifact`-style tasks need this; leaving it
//   unset is fine until that account exists — every other task, including `assembleRelease`,
//   builds without either property set, unsigned.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.play.publisher)
}

kotlin {
    jvmToolchain(17)
}

android {
    namespace = "org.finiteplay.blackjack"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "org.finiteplay.blackjack"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = providers.gradleProperty("finiteplay.versionName").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
    }

    val keystorePath = localProperties.getProperty("finiteplay.upload.keystore")
    val keystorePassword = localProperties.getProperty("finiteplay.upload.keystorePassword")
    if (keystorePath != null && keystorePassword != null && file(keystorePath).exists()) {
        signingConfigs {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = "blackjack-upload"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // `-Pfiniteplay.signReleaseWithDebugKey=true` signs with the debug key so a minified build can be
            // installed on an emulator for release-variant checks (`EXECUTION_PLAN.md` B4a, B8). It never
            // produces an uploadable artifact: Play rejects a debug-signed bundle.
            if (providers.gradleProperty("finiteplay.signReleaseWithDebugKey").isPresent) {
                signingConfig = signingConfigs.getByName("debug")
            } else {
                signingConfigs.findByName("release")?.let { signingConfig = it }
            }
        }
    }
}

play {
    val serviceAccountJson = localProperties.getProperty("finiteplay.play.serviceAccountJson")
    if (serviceAccountJson != null) {
        serviceAccountCredentials.set(file(serviceAccountJson))
    }
    // Every release starts in internal testing, promoted by hand from the Play Console once
    // smoke-tested — never published straight to production from this build.
    track.set("internal")
    defaultToAppBundles.set(true)
}

dependencies {
    implementation(project(":core:ui"))
    implementation(project(":core:session"))
    implementation(project(":core:storage"))
    implementation(project(":games:blackjack:rules"))

    val composeBom = platform(libs.compose.bom)

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.activity.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(testFixtures(project(":core:storage")))
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(testFixtures(project(":games:blackjack:rules")))
    androidTestUtil(libs.androidx.test.orchestrator)
}

// Blackjack is not a solitaire and has nothing to certify, so neither the solitaire layer nor the
// catalog tooling may reach it (`docs/games/blackjack/EXECUTION_PLAN.md` B1).
val noSolitaireLayerTasks = listOf(":solitaire:ui", ":solitaire:catalog", ":tools:catalog").map { forbidden ->
    tasks.register<AssertExcludedProjectTask>("assertAppExcludes${forbidden.split(':').joinToString("") { it.replaceFirstChar(Char::uppercase) }}") {
        group = "verification"
        description = "Fails if $forbidden reaches this app's release runtime classpath."
        ownerProjectPath.set(project.path)
        forbiddenProjectPath.set(forbidden)
        val releaseRuntimeClasspath = configurations.named("releaseRuntimeClasspath")
        resolvedProjectPaths.set(
            provider {
                releaseRuntimeClasspath.get().incoming.resolutionResult.allComponents
                    .mapNotNull { (it.id as? org.gradle.api.artifacts.component.ProjectComponentIdentifier)?.projectPath }
            },
        )
    }
}

tasks.named("check") {
    dependsOn(noSolitaireLayerTasks)
}
