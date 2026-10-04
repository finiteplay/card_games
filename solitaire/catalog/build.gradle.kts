plugins {
    id("finiteplay.shared-layer")
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:cards"))
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":core:cards")))
}

tasks.test {
    useJUnit()
}
