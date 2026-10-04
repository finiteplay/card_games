package org.finiteplay.buildlogic

import org.gradle.api.DefaultTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction

/**
 * Fails the build when [forbiddenProjectPath] appears among [resolvedProjectPaths].
 * Backs the E0 gate proving `:app`'s runtime classpath excludes `:tools:catalog`.
 * [resolvedProjectPaths] is a lazy [ListProperty] so resolving the classpath happens at
 * task-execution time, not eagerly during configuration.
 */
abstract class AssertExcludedProjectTask : DefaultTask() {

    @get:Input
    abstract val resolvedProjectPaths: ListProperty<String>

    @get:Input
    abstract val forbiddenProjectPath: Property<String>

    @get:Input
    abstract val ownerProjectPath: Property<String>

    @TaskAction
    fun assertExcluded() {
        val paths = resolvedProjectPaths.get()
        val forbidden = forbiddenProjectPath.get()
        if (containsForbiddenProjectPath(paths, forbidden)) {
            throw IllegalStateException(
                "${ownerProjectPath.get()} runtime classpath must not resolve $forbidden, " +
                    "but it was found among: $paths",
            )
        }
    }
}

/** Pure logic extracted for unit testing without a live Gradle resolution. */
fun containsForbiddenProjectPath(resolvedProjectPaths: List<String>, forbiddenProjectPath: String): Boolean =
    resolvedProjectPaths.any { it == forbiddenProjectPath }
