plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":games:klondike:rules"))
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":games:klondike:rules")))
}

tasks.test {
    useJUnit()
    // Test classes across forks: this module's slowest classes are independent of each other,
    // and a fork each lets them overlap instead of queueing behind one JVM.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
