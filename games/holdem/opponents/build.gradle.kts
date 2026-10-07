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
    filter { excludeTestsMatching("*FieldStrengthTest") }
}

tasks.register<Test>("fieldStrengthTest") {
    description = "Plays thousands of seeded tournaments to measure the field against the exploit strategies; outside check."
    group = "verification"
    testClassesDirs = sourceSets.test.get().output.classesDirs
    classpath = sourceSets.test.get().runtimeClasspath
    useJUnit()
    maxHeapSize = "2g"
    filter { includeTestsMatching("*FieldStrengthTest") }
    testLogging.showStandardStreams = true
    environment("FIELD_N", System.getenv("FIELD_N") ?: "2000")
    outputs.upToDateWhen { false }
}
