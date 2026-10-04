plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("org.finiteplay.klondike.tools.catalog.MainKt")
    applicationName = "catalog-tool"
    // Catalog scans hold a visited-state map per worker thread. The default heap OOMs on a
    // deep Trivial check ("The paging file is too small" on Windows, killing the JVM), so
    // give the tool room; it is desktop-only and never ships.
    applicationDefaultJvmArgs = listOf("-Xmx12g")
}

tasks.jar {
    archiveBaseName.set("catalog-tool")
}

dependencies {
    implementation(project(":games:klondike:rules"))
    implementation(project(":solitaire:catalog"))
    implementation(project(":games:klondike:solver"))
    implementation(project(":games:spider:rules"))
    implementation(project(":games:spider:solver"))
    implementation(project(":games:freecell:rules"))
    implementation(project(":games:freecell:solver"))
    testImplementation(libs.junit4)
    testImplementation(testFixtures(project(":games:klondike:rules")))
}

/**
 * The catalog gate (`docs/games/klondike/ACCEPTANCE.md`). Verifies committed evidence — format,
 * versions, counts, hashes, uniqueness, and a replayed certificate sample — and never solves.
 *
 * A forked process, not daemon work: this loads the rules module and replays lines, and the
 * project's rule is that solver-adjacent work stays out of the Gradle daemon. It passes with a
 * notice while no catalog is committed, so the gate is live before D1b generates one.
 */
val verifyDealCatalogs by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verifies the committed deal catalogs against their manifest. Never solves."
    mainClass.set("org.finiteplay.klondike.tools.catalog.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
    args("verify-catalogs")
}

/**
 * Spider's own catalog gate, kept separate from Klondike's [verifyDealCatalogs] so one game's
 * catalog failing never looks like the other's (`docs/games/spider/DEALS.md`).
 */
val verifySpiderDealCatalogs by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verifies the committed Spider deal catalogs against their manifest. Never solves."
    mainClass.set("org.finiteplay.spider.tools.catalog.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
    args("verify")
}

/**
 * Not a gate — this is the generator itself, and it solves. Run by hand to (re)certify the ONE-
 * and TWO-suit catalogs; `verifySpiderDealCatalogs` is what CI runs.
 */
val buildSpiderDealCatalogs by tasks.registering(JavaExec::class) {
    group = "other"
    description = "Certifies and writes the Spider ONE/TWO deal catalogs, solutions blob, and manifest."
    mainClass.set("org.finiteplay.spider.tools.catalog.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
    args("build")
}

/**
 * One-off: packages the external plspider campaign's already-verified FOUR-suit solutions
 * (`docs/games/spider/FOUR_SUIT_CATALOG.md`) into `four.catalog` + the shared solutions blob.
 * `verified/` there already holds independently-replayed certificates; this re-verifies them again
 * via `writeCatalogFromExternalSolutions` before shipping, same as every other catalog write.
 */
val importFourSuitExternal by tasks.registering(JavaExec::class) {
    group = "other"
    description = "Imports the plspider campaign's verified FOUR-suit solutions into four.catalog."
    mainClass.set("org.finiteplay.spider.tools.catalog.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
    args("import-external", "FOUR")
    // Resolved when the task runs, so a build that never imports does not need the property.
    argumentProviders.add(
        org.gradle.process.CommandLineArgumentProvider {
            listOf(
                providers.gradleProperty("fourSuitVerifiedDir").orNull
                    ?: error("pass -PfourSuitVerifiedDir=<the plspider campaign's verified/ directory>"),
            )
        },
    )
}

/** Scratch, one-off: drops solutions-blob entries no longer claimed by any committed catalog. */
val pruneOrphanSpiderSolutions by tasks.registering(JavaExec::class) {
    group = "other"
    description = "One-off: prunes spider_solutions.bin.gz entries not claimed by any current catalog."
    mainClass.set("org.finiteplay.spider.tools.catalog.PruneOrphanSpiderSolutionsKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
}

/**
 * Not a gate — a one-off re-encode. Bit-packs the committed verification artifact with
 * `CompactSolutionCodec` and writes the `assets/solutions.bin` the app ships, so Hint can follow a
 * known winning line instead of only ever searching live (`docs/games/spider/DEALS.md`).
 */
val compactSpiderSolutions by tasks.registering(JavaExec::class) {
    group = "other"
    description = "Bit-packs the committed Spider solutions blob into the shipped assets/solutions.bin."
    mainClass.set("org.finiteplay.spider.tools.catalog.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
    args("compact")
}

/**
 * FreeCell's own catalog gate, kept separate from the other games' so one game's catalog failing
 * never looks like another's (`docs/games/freecell/DEALS.md`).
 */
val verifyFreeCellDealCatalogs by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verifies the committed FreeCell deal catalog against its manifest. Never solves."
    mainClass.set("org.finiteplay.freecell.tools.catalog.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
    args("verify")
}

/**
 * Not a gate — this is the generator itself, and it solves. Run by hand to (re)certify the
 * catalog; `verifyFreeCellDealCatalogs` is what CI runs.
 */
val buildFreeCellDealCatalogs by tasks.registering(JavaExec::class) {
    group = "other"
    description = "Certifies and writes the FreeCell deal catalog, solutions blob, and manifest."
    mainClass.set("org.finiteplay.freecell.tools.catalog.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
    args("build")
}

/**
 * Not a gate — a one-off re-solve. Re-solves every already-certified FreeCell seed with
 * `BestFirstSolver` for a much shorter line, writes the shrunk certificates back to the
 * verification artifact, and encodes the bit-packed `assets/solutions.bin` the app ships.
 */
val shrinkFreeCellSolutions by tasks.registering(JavaExec::class) {
    group = "other"
    description = "Re-solves the FreeCell catalog with BestFirstSolver for shorter certificates, then encodes assets/solutions.bin."
    mainClass.set("org.finiteplay.freecell.tools.catalog.MainKt")
    classpath = sourceSets.main.get().runtimeClasspath
    workingDir = rootDir
    args("shrink")
}

tasks.test {
    useJUnit()
    // Test classes across forks: the ruleset and robustness suites are independent of each
    // other, and a fork each lets them overlap instead of queueing behind one JVM.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
}
