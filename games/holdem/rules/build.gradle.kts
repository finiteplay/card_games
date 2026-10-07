plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

// No Android, no solitaire layer, no solver (`docs/games/holdem/DESIGN.md` "Architecture"). Only the
// two shared modules a card game of any kind can use.
dependencies {
    api(project(":core:cards"))
    api(project(":core:session"))
    testImplementation(libs.junit4)
    testFixturesApi(project(":core:cards"))
    testFixturesApi(project(":core:session"))
}

tasks.test {
    useJUnit()
    // The seven-card evaluator test walks 133,784,560 hands; it is memory-light but slow.
    maxHeapSize = "2g"
}
