package org.finiteplay.freecell.storage

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.test.runTest
import org.finiteplay.core.storage.FakeDataStores
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FreeCellHistoryStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private fun historyStore(directory: File) = FreeCellHistoryStore(directory, dataStoreFactory = FakeDataStores::create)

    private fun record(resultId: String, gameId: String = "game-$resultId", outcome: FreeCellOutcome = FreeCellOutcome.WIN) =
        FreeCellHistoryRecord(
            gameId = gameId,
            resultId = resultId,
            outcome = outcome,
            elapsedMillis = 12_345L,
            moveCount = 87,
            timestampMillis = 1_700_000_000_000L,
        )

    @Test
    fun `a fresh directory has empty history`() = runTest {
        assertEquals(emptyList<FreeCellHistoryRecord>(), historyStore(folder.newFolder()).current())
    }

    @Test
    fun `upsert then a fresh reader sees the record`() = runTest {
        val dir = folder.newFolder()
        historyStore(dir).upsert(record("r1"))

        assertEquals(listOf(record("r1")), historyStore(dir).current())
    }

    @Test
    fun `upserting the same resultId twice does not duplicate it`() = runTest {
        val store = historyStore(folder.newFolder())
        store.upsert(record("r1"))
        store.upsert(record("r1"))

        assertEquals(1, store.current().size)
    }

    @Test
    fun `a garbage history file reads back as empty instead of throwing`() = runTest {
        val dir = folder.newFolder()
        FakeDataStores.corrupt(dir, "history")

        assertEquals(emptyList<FreeCellHistoryRecord>(), historyStore(dir).current())
    }

    @Test
    fun `clearing leaves no history behind`() = runTest {
        val dir = folder.newFolder()
        val store = historyStore(dir)
        store.upsert(record("r1"))
        store.clear()

        assertEquals(emptyList<FreeCellHistoryRecord>(), historyStore(dir).current())
    }

    @Test
    fun `a win and a loss both round-trip through a simulated restart`() = runTest {
        val dir = folder.newFolder()
        val store = historyStore(dir)
        store.upsert(record("r1", outcome = FreeCellOutcome.WIN))
        store.upsert(record("r2", outcome = FreeCellOutcome.LOSS))

        val all = historyStore(dir).current()
        assertEquals(setOf(FreeCellOutcome.WIN, FreeCellOutcome.LOSS), all.map { it.outcome }.toSet())
    }

    @Test
    fun `hints and solution length round-trip, and a v1 blob decodes with neither`() = runTest {
        val dir = folder.newFolder()
        historyStore(dir).upsert(record("r1").copy(hintsUsed = 2, solutionMoveCount = 88))
        val back = historyStore(dir).current().single()
        assertEquals(2, back.hintsUsed)
        assertEquals(88, back.solutionMoveCount)

        val v1 = folder.newFolder()
        val bytes = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(bytes).use { out ->
            out.writeInt(1)
            out.writeInt(1)
            out.writeUTF("g")
            out.writeUTF("r")
            out.writeInt(FreeCellOutcome.WIN.ordinal)
            out.writeLong(1L)
            out.writeInt(80)
            out.writeLong(2L)
        }
        FakeDataStores.create(v1, "history").edit {
            it[androidx.datastore.preferences.core.stringPreferencesKey("records")] =
                java.util.Base64.getEncoder().encodeToString(bytes.toByteArray())
        }
        val only = historyStore(v1).current().single()
        assertEquals(0, only.hintsUsed)
        assertEquals(0, only.solutionMoveCount)
    }
}
