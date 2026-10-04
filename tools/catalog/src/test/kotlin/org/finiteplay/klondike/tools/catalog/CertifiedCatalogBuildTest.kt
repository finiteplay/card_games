package org.finiteplay.klondike.tools.catalog

import java.io.File
import java.nio.file.Files
import org.finiteplay.solitaire.catalog.catalog.CatalogFormat
import org.finiteplay.solitaire.catalog.catalog.CatalogLoadResult
import org.finiteplay.solitaire.catalog.catalog.CatalogMetadata
import org.finiteplay.solitaire.catalog.catalog.encodeCatalog
import org.finiteplay.solitaire.catalog.catalog.loadCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves the D1b pipeline on synthetic catalogs rather than the real ones, which do not exist
 * yet: the plan forbids generating certificates before the rules freeze
 * (`docs/games/klondike/EXECUTION_PLAN.md` "Execution Rules"), and a gate that only works once the
 * artifact exists is a gate nobody has run.
 *
 * What is proven here is everything except "the real seeds win": the format the catalogs are
 * written in, the manifest round trip, the partition mapping, and every rejection the gate is
 * specified to make.
 */
class CertifiedCatalogBuildTest {

    private fun tempDir(): File = Files.createTempDirectory("catalogs").toFile().also { it.deleteOnExit() }

    private fun syntheticCatalog(dir: File, level: CatalogLevel, seeds: List<Long>): ByteArray {
        val bytes = encodeCatalog(
            CatalogMetadata(
                catalogVersion = 1,
                rulesVersion = 1,
                shuffleVersion = 1,
                solverVersion = 1,
                partitionId = level.partitionId,
            ),
            seeds.map { it.toULong() },
        )
        File(dir, catalogFileName(level)).writeBytes(bytes)
        return bytes
    }

    /** A manifest for [levels] whose hashes match what was actually written. */
    private fun writeManifestFor(dir: File, levels: Map<CatalogLevel, ByteArray>) {
        val entries = levels.map { (level, bytes) ->
            CatalogManifestEntry(
                level = level,
                partitionId = level.partitionId,
                recordCount = (bytes.size - CatalogFormat.HEADER_SIZE_V1) / CatalogFormat.SEED_RECORD_SIZE,
                payloadHash = sha256Hex(bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)),
                fileHash = sha256Hex(bytes),
                certified = false,
            )
        }
        File(dir, MANIFEST_NAME).writeText(manifestTextFor(entries))
    }

    @Test
    fun everyLevelGetsItsOwnStablePartitionId() {
        // Fixed by the format, not by iteration order: a saved traversal position names its
        // partition by id, so a renumber silently moves a player into another level.
        assertEquals(1.toByte(), CatalogLevel.TRIVIAL.partitionId)
        assertEquals(6.toByte(), CatalogLevel.INSANE.partitionId)
        assertEquals(
            CatalogLevel.entries.size,
            CatalogLevel.entries.map { it.partitionId }.toSet().size,
        )
        assertFalse(
            "no level may take the unpartitioned id",
            CatalogLevel.entries.any { it.partitionId == CatalogFormat.PARTITION_NONE },
        )
    }

    @Test
    fun aWrittenCatalogLoadsBackWithItsPartitionAndSeeds() {
        val dir = tempDir()
        val seeds = listOf(11L, 22L, 33L, 44L)
        syntheticCatalog(dir, CatalogLevel.MEDIUM, seeds)

        val result = loadCatalog(File(dir, catalogFileName(CatalogLevel.MEDIUM)).readBytes())

        check(result is CatalogLoadResult.Valid) { "expected Valid, got $result" }
        assertEquals(CatalogLevel.MEDIUM.partitionId, result.header.partitionId)
        assertEquals(seeds.map { it.toULong() }, result.seeds)
    }

    @Test
    fun theManifestRoundTrips() {
        val entries = listOf(
            CatalogManifestEntry(CatalogLevel.TRIVIAL, 1, 10, "a".repeat(64), "b".repeat(64), certified = true),
            CatalogManifestEntry(CatalogLevel.INSANE, 6, 7, "c".repeat(64), "d".repeat(64), certified = false),
        )

        assertEquals(entries, parseManifest(manifestTextFor(entries)))
    }

    @Test
    fun aMissingManifestPassesRatherThanFailing() {
        // D1b has not generated yet. The gate must be runnable and green before the artifact
        // exists, or it lands untested on the same day the catalog does.
        val log = ArrayList<String>()
        val passed = verifyCertifiedCatalogs(catalogDir = tempDir().path, solutionsPath = "nope.bin", log = log::add)

        assertTrue(passed)
        assertTrue(log.any { it.contains("nothing committed to verify") })
    }

    @Test
    fun theGateAcceptsConsistentUncertifiedCatalogs() {
        val dir = tempDir()
        val written = mapOf(
            CatalogLevel.TRIVIAL to syntheticCatalog(dir, CatalogLevel.TRIVIAL, listOf(1L, 2L, 3L)),
            CatalogLevel.INSANE to syntheticCatalog(dir, CatalogLevel.INSANE, listOf(90L, 91L)),
        )
        writeManifestFor(dir, written)

        assertTrue(verifyCertifiedCatalogs(catalogDir = dir.path, solutionsPath = "nope.bin", log = {}))
    }

    @Test
    fun theGateRejectsACatalogWhoseBytesChangedAfterTheManifest() {
        val dir = tempDir()
        val bytes = syntheticCatalog(dir, CatalogLevel.TRIVIAL, listOf(1L, 2L, 3L))
        writeManifestFor(dir, mapOf(CatalogLevel.TRIVIAL to bytes))

        // Rewrite the same catalog with a different seed set: internally valid, wrong hash.
        syntheticCatalog(dir, CatalogLevel.TRIVIAL, listOf(4L, 5L, 6L))

        val log = ArrayList<String>()
        assertFalse(verifyCertifiedCatalogs(catalogDir = dir.path, solutionsPath = "nope.bin", log = log::add))
        assertTrue(log.any { it.contains("file hash") })
    }

    @Test
    fun theGateRejectsASeedClaimedByTwoLevels() {
        val dir = tempDir()
        val written = mapOf(
            CatalogLevel.TRIVIAL to syntheticCatalog(dir, CatalogLevel.TRIVIAL, listOf(1L, 2L, 3L)),
            CatalogLevel.EASY to syntheticCatalog(dir, CatalogLevel.EASY, listOf(3L, 4L)),
        )
        writeManifestFor(dir, written)

        val log = ArrayList<String>()
        assertFalse(verifyCertifiedCatalogs(catalogDir = dir.path, solutionsPath = "nope.bin", log = log::add))
        assertTrue(log.any { it.contains("appears in both") })
    }

    @Test
    fun theGateRejectsAManifestEntryWithNoCatalogCommitted() {
        val dir = tempDir()
        val bytes = syntheticCatalog(dir, CatalogLevel.HARD, listOf(1L, 2L))
        writeManifestFor(dir, mapOf(CatalogLevel.HARD to bytes))
        File(dir, catalogFileName(CatalogLevel.HARD)).delete()

        val log = ArrayList<String>()
        assertFalse(verifyCertifiedCatalogs(catalogDir = dir.path, solutionsPath = "nope.bin", log = log::add))
        assertTrue(log.any { it.contains("not committed") })
    }

    @Test
    fun theGateRequiresACertificateSourceForACertifiedLevel() {
        val dir = tempDir()
        val bytes = syntheticCatalog(dir, CatalogLevel.TRIVIAL, listOf(1L, 2L))
        val entry = CatalogManifestEntry(
            level = CatalogLevel.TRIVIAL,
            partitionId = CatalogLevel.TRIVIAL.partitionId,
            recordCount = 2,
            payloadHash = sha256Hex(bytes.copyOfRange(CatalogFormat.HEADER_SIZE_V1, bytes.size)),
            fileHash = sha256Hex(bytes),
            certified = true,
        )
        File(dir, MANIFEST_NAME).writeText(manifestTextFor(listOf(entry)))

        val log = ArrayList<String>()
        assertFalse(verifyCertifiedCatalogs(catalogDir = dir.path, solutionsPath = "nope.bin", log = log::add))
        assertTrue(log.any { it.contains("no solutions asset") })
    }
}
