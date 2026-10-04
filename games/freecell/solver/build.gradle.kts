plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":games:freecell:rules"))
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":games:freecell:rules")))
}

tasks.test {
    useJUnit()
}
