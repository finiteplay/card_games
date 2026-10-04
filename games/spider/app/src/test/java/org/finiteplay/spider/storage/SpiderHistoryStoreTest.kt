package org.finiteplay.spider.storage

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.spider.layout.SuitCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Base64

class SpiderHistoryStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun historyStore(directory: File) = SpiderHistoryStore(directory, dataStoreFactory = FakeDataStores::create)

    private fun record(
        resultId: String,
        gameId: String = "game-$resultId",
        outcome: SpiderOutcome = SpiderOutcome.WIN,
        suitCount: SuitCount = SuitCount.ONE,
        hints: Int = 0,
    ) = SpiderHistoryRecord(
        gameId = gameId,
        resultId = resultId,
        outcome = outcome,
        suitCount = suitCount,
        elapsedMillis = 12_345L,
        moveCount = 87,
        timestampMillis = 1_700_000_000_000L,
        hintsUsed = hints,
    )

    /** The exact pre-hints-used v1 encoding: a version of 1 and no trailing hint-count int per record. */
    private fun encodeLegacyV1(records: List<SpiderHistoryRecord>): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(records.size)
            for (r in records) {
                out.writeUTF(r.gameId)
                out.writeUTF(r.resultId)
                out.writeInt(r.outcome.ordinal)
                out.writeInt(r.suitCount.ordinal)
                out.writeLong(r.elapsedMillis)
                out.writeInt(r.moveCount)
                out.writeLong(r.timestampMillis)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    @Test
    fun `a fresh directory has empty history`() = runTest {
        assertEquals(emptyList<SpiderHistoryRecord>(), historyStore(tempFolder.newFolder()).current())
    }

    @Test
    fun `upsert then a fresh reader sees the record, hints included`() = runTest {
        val dir = tempFolder.newFolder()
        historyStore(dir).upsert(record("r1", hints = 3))

        assertEquals(listOf(record("r1", hints = 3)), historyStore(dir).current())
    }

    @Test
    fun `upserting the same resultId twice does not duplicate it`() = runTest {
        val store = historyStore(tempFolder.newFolder())
        store.upsert(record("r1"))
        store.upsert(record("r1"))

        assertEquals(1, store.current().size)
    }

    @Test
    fun `a garbage history file reads back as empty instead of throwing`() = runTest {
        val dir = tempFolder.newFolder()
        FakeDataStores.corrupt(dir, "history")

        assertEquals(emptyList<SpiderHistoryRecord>(), historyStore(dir).current())
    }

    @Test
    fun `a genuine pre-hints v1 blob decodes with every record defaulting to zero hints`() = runTest {
        val dir = tempFolder.newFolder()
        val legacyRecords = listOf(record("r1"), record("r2", outcome = SpiderOutcome.LOSS))
        val raw = FakeDataStores.create(dir, "history")
        raw.edit { it[stringPreferencesKey("records")] = encodeLegacyV1(legacyRecords) }

        val all = historyStore(dir).current()

        assertEquals(2, all.size)
        assertTrue(all.all { it.hintsUsed == 0 })
        assertEquals(setOf("r1", "r2"), all.map { it.resultId }.toSet())
    }

    @Test
    fun `a hint count round-trips through a simulated restart`() = runTest {
        val dir = tempFolder.newFolder()
        historyStore(dir).upsert(record("r1", hints = 4))

        assertEquals(4, historyStore(dir).current().single().hintsUsed)
    }

    @Test
    fun `a solution length round-trips, and a v2 blob decodes with none`() = runTest {
        val dir = tempFolder.newFolder()
        historyStore(dir).upsert(record("r1").copy(solutionMoveCount = 123))
        assertEquals(123, historyStore(dir).current().single().solutionMoveCount)

        val v2 = tempFolder.newFolder()
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(2)
            out.writeInt(1)
            out.writeUTF("g")
            out.writeUTF("r")
            out.writeInt(SpiderOutcome.WIN.ordinal)
            out.writeInt(SuitCount.ONE.ordinal)
            out.writeLong(1L)
            out.writeInt(80)
            out.writeLong(2L)
            out.writeInt(3)
        }
        FakeDataStores.create(v2, "history").edit {
            it[stringPreferencesKey("records")] = java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())
        }
        val only = historyStore(v2).current().single()
        assertEquals(3, only.hintsUsed)
        assertEquals(0, only.solutionMoveCount)
    }
}
