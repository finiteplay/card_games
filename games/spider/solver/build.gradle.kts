plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

application {
    mainClass.set("org.finiteplay.spider.solver.SurveyKt")
    // The survey is memory-hungry: every worker holds its own transposition set. Raised from 6g
    // after an S6 campaign run requested a cache far larger than 6g could ever hold (an unsized
    // `maxNodes` on the campaign CLI, not this cap, was the actual mistake — see
    // `docs/games/spider/EXECUTION_PLAN.md` "S6" — but 6g leaves so little room that even a
    // correctly-sized run risks it under 16 threads' worth of transposition caches at once).
    // 20g leaves real headroom below this machine's 32g for the OS and everything else running.
    applicationDefaultJvmArgs = listOf("-Xmx20g")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":games:spider:rules"))
    implementation(project(":core:cards"))
    testImplementation(libs.junit4)
}

tasks.test {
    useJUnit()
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
