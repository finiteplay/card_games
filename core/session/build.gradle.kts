plugins {
    id("finiteplay.shared-layer")
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// Deliberately dependency-free, and not even on `:core:cards`: an undo stack and an
// append-only log know nothing about cards, and a dependency here would be the first step
// towards this module knowing what a pile is (`docs/ARCHITECTURE.md`).
dependencies {
    testImplementation(libs.junit4)
}

tasks.test {
    useJUnit()
}
