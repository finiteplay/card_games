package org.finiteplay.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

/**
 * Fails when a shared-layer source file names a specific game. The layering in
 * `docs/ARCHITECTURE.md` runs one way only, from a game down through the solitaire layer
 * to core, and a dependency declaration alone does not enforce it: nothing stops someone
 * writing `if (game == "klondike")` inside `core:ui`. This makes the rule a gate.
 *
 * The check is textual on purpose. It catches the reference in a comment, a string, or a
 * resource name, not only in an import that the compiler would already reject.
 */
abstract class AssertNoGameReferencesTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input
    abstract val forbiddenNames: ListProperty<String>

    @get:Input
    abstract val ownerProjectPath: Property<String>

    @TaskAction
    fun assertNoGameReferences() {
        val forbidden = forbiddenNames.get()
        val offenders = sources.files
            .filter { it.isFile }
            .mapNotNull { file ->
                val hits = findGameReferences(file.readText(), forbidden)
                if (hits.isEmpty()) null else file.path to hits
            }
        if (offenders.isNotEmpty()) {
            val detail = offenders.joinToString("\n") { (path, hits) ->
                "  $path -> ${hits.joinToString(", ")}"
            }
            throw IllegalStateException(
                "${ownerProjectPath.get()} is a shared layer and must not name a specific game, " +
                    "but these files do:\n$detail",
            )
        }
    }
}

/**
 * Returns the forbidden names appearing in [content], case-insensitively. Pure logic,
 * extracted so it is unit-testable without a live Gradle build.
 */
fun findGameReferences(content: String, forbiddenNames: List<String>): List<String> {
    val lowered = content.lowercase()
    return forbiddenNames.filter { lowered.contains(it.lowercase()) }
}
