import org.finiteplay.buildlogic.AssertNoGameReferencesTask

// Applied by every module under `core/` and `solitaire/`. Marks the module as shared and
// wires the gate that keeps it that way (`docs/ARCHITECTURE.md`).

val assertNoGameReferences by tasks.registering(AssertNoGameReferencesTask::class) {
    group = "verification"
    description = "Fails if this shared module names a specific game."
    ownerProjectPath.set(project.path)
    // Games that exist plus the two the restructure was aimed at, so the gate is already
    // armed when their modules land rather than needing to be remembered then.
    forbiddenNames.set(listOf("klondike", "spider", "blackjack", "freecell"))
    sources.from(
        fileTree(layout.projectDirectory.dir("src")) {
            include("**/*.kt", "**/*.kts", "**/*.xml", "**/*.pro")
        },
    )
}

tasks.matching { it.name == "check" }.configureEach {
    dependsOn(assertNoGameReferences)
}
