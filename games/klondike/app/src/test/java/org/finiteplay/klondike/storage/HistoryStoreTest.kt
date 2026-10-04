package org.finiteplay.klondike.storage

import org.finiteplay.klondike.deal.DifficultyTier
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.test.runTest
import org.finiteplay.klondike.board.DrawMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.util.Base64

class HistoryStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun record(
        resultId: String,
        gameId: String = "game-$resultId",
        outcome: Outcome = Outcome.WIN,
        drawMode: DrawMode = DrawMode.ONE,
    ) = HistoryRecord(
        gameId = gameId,
        resultId = resultId,
        outcome = outcome,
        elapsedMillis = 12_345L,
        moveCount = 87,
        timestampMillis = 1_700_000_000_000L,
        drawMode = drawMode,
    )

    /** The exact pre-draw-three encoding: no leading marker, no per-record draw-mode byte. */
    private fun encodeLegacyV1(records: List<HistoryRecord>): String {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(records.size)
            for (r in records) {
                out.writeUTF(r.gameId)
                out.writeUTF(r.resultId)
                out.writeByte(r.outcome.ordinal)
                out.writeLong(r.elapsedMillis)
                out.writeInt(r.moveCount)
                out.writeLong(r.timestampMillis)
            }
        }
        return Base64.getEncoder().encodeToString(bytes.toByteArray())
    }

    @Test
    fun `a fresh directory has empty history`() = runTest {
        assertEquals(emptyList<HistoryRecord>(), historyStore(tempFolder.newFolder()).all())
    }

    @Test
    fun `upsert then a fresh reader sees the record`() = runTest {
        val dir = tempFolder.newFolder()
        historyStore(dir).upsert(record("r1"))

        val all = historyStore(dir).all()

        assertEquals(listOf(record("r1")), all)
    }

    @Test
    fun `upserting the same resultId twice does not duplicate it`() = runTest {
        val store = historyStore(tempFolder.newFolder())
        store.upsert(record("r1"))
        store.upsert(record("r1"))
        store.upsert(record("r1"))

        assertEquals(1, store.all().size)
    }

    @Test
    fun `upserting the same resultId again replaces its content`() = runTest {
        val store = historyStore(tempFolder.newFolder())
        store.upsert(record("r1", outcome = Outcome.LOSS))
        store.upsert(record("r1", outcome = Outcome.WIN))

        val all = store.all()
        assertEquals(1, all.size)
        assertEquals(Outcome.WIN, all.single().outcome)
    }

    @Test
    fun `distinct resultIds accumulate independently`() = runTest {
        val store = historyStore(tempFolder.newFolder())
        store.upsert(record("r1"))
        store.upsert(record("r2"))
        store.upsert(record("r3"))

        assertEquals(setOf("r1", "r2", "r3"), store.all().map { it.resultId }.toSet())
    }

    @Test
    fun `a garbage history file reads back as empty instead of throwing`() = runTest {
        val dir = tempFolder.newFolder()
        FakeDataStores.corrupt(dir, "history")

        assertEquals(emptyList<HistoryRecord>(), historyStore(dir).all())
    }

    @Test
    fun `a record's draw mode round-trips through a simulated restart`() = runTest {
        val dir = tempFolder.newFolder()
        historyStore(dir).upsert(record("r1", drawMode = DrawMode.THREE))

        assertEquals(DrawMode.THREE, historyStore(dir).all().single().drawMode)
    }

    @Test
    fun `a genuine pre-draw-three v1 blob decodes with every record defaulting to DrawMode ONE`() = runTest {
        val dir = tempFolder.newFolder()
        val legacyRecords = listOf(record("r1"), record("r2", outcome = Outcome.LOSS))
        val raw = FakeDataStores.create(dir, "history")
        raw.edit { it[stringPreferencesKey("records")] = encodeLegacyV1(legacyRecords) }

        val all = historyStore(dir).all()

        assertEquals(2, all.size)
        assertTrue(all.all { it.drawMode == DrawMode.ONE })
        assertEquals(setOf("r1", "r2"), all.map { it.resultId }.toSet())
    }

    @Test
    fun `history survives many upserts, including across a simulated restart`() = runTest {
        val dir = tempFolder.newFolder()
        val firstHalf = historyStore(dir)
        (0 until 25).forEach { i -> firstHalf.upsert(record("r$i")) }

        val secondHalf = historyStore(dir)
        (25 until 50).forEach { i -> secondHalf.upsert(record("r$i")) }

        val all = historyStore(dir).all()
        assertEquals(50, all.size)
        assertTrue(all.map { it.resultId }.toSet().containsAll((0 until 50).map { "r$it" }))
    }

    @Test
    fun `a level round-trips through a simulated restart, and a record without one stays without`() = runTest {
        val dir = tempFolder.newFolder()
        val store = historyStore(dir)
        store.upsert(record("r1").copy(difficulty = DifficultyTier.HARD))
        store.upsert(record("r2"))

        val all = historyStore(dir).all().associateBy { it.resultId }
        assertEquals(DifficultyTier.HARD, all.getValue("r1").difficulty)
        assertEquals(null, all.getValue("r2").difficulty)
    }

    @Test
    fun `a genuine v3 blob decodes with no level on any record`() = runTest {
        val dir = tempFolder.newFolder()
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeByte(-2)
            out.writeInt(1)
            out.writeUTF("g")
            out.writeUTF("r")
            out.writeByte(Outcome.WIN.ordinal)
            out.writeLong(1L)
            out.writeInt(40)
            out.writeLong(2L)
            out.writeByte(DrawMode.ONE.ordinal)
            out.writeInt(2)
            out.writeInt(35)
        }
        val raw = FakeDataStores.create(dir, "history")
        raw.edit { it[stringPreferencesKey("records")] = Base64.getEncoder().encodeToString(bytes.toByteArray()) }

        val only = historyStore(dir).all().single()
        assertEquals(35, only.solutionMoveCount)
        assertEquals(null, only.difficulty)
    }
}
