plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-test-fixtures`
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:cards"))
    api(project(":core:session"))
    testImplementation(libs.junit4)
    testFixturesApi(project(":core:cards"))
    testFixturesApi(project(":core:session"))
}

tasks.test {
    useJUnit()
}
