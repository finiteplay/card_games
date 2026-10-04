plugins {
    id("finiteplay.shared-layer")
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

// Deliberately dependency-free: playing cards and a deterministic shuffle are the one
// thing every card game in this repo shares, so this module must never acquire a
// game-shaped dependency (`docs/ARCHITECTURE.md`).
dependencies {
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":core:cards")))
}

tasks.test {
    useJUnit()
}
