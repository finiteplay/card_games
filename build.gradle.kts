plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

val verifyRelease by tasks.registering {
    group = "verification"
    description = "Aggregate gate: runs the individual ACCEPTANCE.md commands."
    dependsOn(
        ":games:klondike:app:testDebugUnitTest",
        ":games:klondike:app:lintDebug",
        ":games:klondike:app:assembleRelease",
    )
}
