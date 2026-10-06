package org.finiteplay.spider.ui.game

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.spider.layout.SuitCount
import org.finiteplay.spider.storage.SpiderSettingsStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Changing the suit count must deal at the count chosen *the first time*. The choice is persisted
 * asynchronously and reaches [SpiderViewModel.settings] only once the write has landed, so a new
 * game that read the persisted setting would still deal at the old count and need the choice made
 * a second time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SuitCountSwitchTest {
    private lateinit var dir: File
    private val writeHold = CompletableDeferred<Unit>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        dir = Files.createTempDirectory("suit-switch").toFile()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        dir.deleteRecursively()
    }

    /** A settings store whose writes wait for [writeHold], as a real DataStore's do in practice. */
    private fun slowStore() = SpiderSettingsStore(dir) { d, name ->
        val delegate = FakeDataStores.create(d, name)
        object : DataStore<Preferences> {
            override val data = delegate.data
            override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                writeHold.await()
                return delegate.updateData(transform)
            }
        }
    }

    @Test
    fun `the status-row picker deals at the chosen count straight away`() {
        val viewModel = SpiderViewModel(initialSeed = 1L, settingsStore = slowStore())
        val other = SuitCount.entries.first { it != viewModel.session.state.suitCount }

        viewModel.setSuitCount(other)

        assertEquals(other, viewModel.session.state.suitCount)
    }

    @Test
    fun `choosing a count in Settings deals at it the first time`() {
        val viewModel = SpiderViewModel(initialSeed = 1L, settingsStore = slowStore())
        val other = SuitCount.entries.first { it != viewModel.session.state.suitCount }

        viewModel.requestSuitCount(other)

        assertEquals(other, viewModel.session.state.suitCount)
    }

    @Test
    fun `switching suit counts and back finds the same hand, and marks nothing played`() {
        val viewModel = SpiderViewModel(
            initialSeed = 1L,
            settingsStore = SpiderSettingsStore(dir, FakeDataStores::create),
            traversalStore = org.finiteplay.spider.storage.SpiderTraversalStore(dir, FakeDataStores::create),
            dealProgressStore = org.finiteplay.core.storage.DealProgressStore(dir, dataStoreFactory = FakeDataStores::create),
        )
        val first = viewModel.session.state.suitCount
        val other = SuitCount.entries.first { it != first }
        // The opening game is dealt from the constructor's seed; the first switch there and back
        // reaches the sequence's own first hand, which every further switch must find again.
        viewModel.setSuitCount(other)
        viewModel.setSuitCount(first)
        val firstDeal = viewModel.dealNumber
        val firstSeed = viewModel.session.state.seed

        repeat(3) {
            viewModel.setSuitCount(other)
            viewModel.setSuitCount(first)
        }

        assertEquals(first, viewModel.session.state.suitCount)
        assertEquals(firstDeal, viewModel.dealNumber)
        assertEquals(firstSeed, viewModel.session.state.seed)
        assertEquals("no deal was played", emptyMap<Long, Any>(), viewModel.dealProgress)
    }

    @Test
    fun `a played game's deal is not given back when its suit count is left`() {
        val viewModel = SpiderViewModel(
            initialSeed = 1L,
            settingsStore = SpiderSettingsStore(dir, FakeDataStores::create),
            traversalStore = org.finiteplay.spider.storage.SpiderTraversalStore(dir, FakeDataStores::create),
        )
        val first = viewModel.session.state.suitCount
        val other = SuitCount.entries.first { it != first }
        // Reach a game dealt from the sequence, then play it.
        viewModel.setSuitCount(other)
        viewModel.setSuitCount(first)
        val firstDeal = viewModel.dealNumber
        viewModel.loadFixtureForDebugging(viewModel.session.state.copy(moveCount = 3))

        viewModel.requestSuitCount(other)
        viewModel.confirmPendingAction()
        viewModel.setSuitCount(first)

        assertEquals("the played deal was used up", firstDeal + 1, viewModel.dealNumber)
    }
}
