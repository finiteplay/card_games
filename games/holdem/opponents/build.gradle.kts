plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

// The opponents see a seat's view and nothing else, and the module boundary makes that a
// compile-time fact: it depends on the rules' types but never on the app (`docs/games/holdem/DESIGN.md`
// "Architecture").
dependencies {
    api(project(":games:holdem:rules"))
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":games:holdem:rules")))
}

tasks.test {
    useJUnit()
    maxHeapSize = "2g"
}
