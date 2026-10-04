plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:cards"))
    api(project(":core:session"))
    testImplementation(libs.junit4)
}

tasks.test {
    useJUnit()
}

/**
 * Dev tool, not shipped: decodes a pulled `active_game.preferences_pb` and prints the board it
 * reconstructs to. See `games/spider/tools/inspect-save.ps1`, which pulls the file and calls
 * this task; `InspectSave.kt` (test sourceset) does the decoding.
 */
tasks.register<JavaExec>("inspectSave") {
    group = "spider"
    description = "Print the board a pulled Spider save file reconstructs to. Usage: -Psave=<path>"
    dependsOn("testClasses")
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.finiteplay.spider.session.tools.InspectSaveKt")
    val save = providers.gradleProperty("save")
    args = listOf(
        save.orNull ?: error("Pass the pulled save file: -Psave=<path to active_game.preferences_pb>"),
    )
}
