package org.finiteplay.freecell.ui.game

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.finiteplay.core.storage.DealProgress
import org.finiteplay.core.storage.DealStatus
import org.finiteplay.freecell.rules.legalMoves
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FreeCellDealProgressTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `a dealt board nobody has touched has no progress`() {
        val viewModel = FreeCellViewModel(initialSeed = 7L)
        assertEquals(emptyMap<Long, DealProgress>(), viewModel.dealProgress)
    }

    @Test
    fun `the first move marks the deal played`() {
        val viewModel = FreeCellViewModel(initialSeed = 7L)
        val seed = viewModel.session.state.seed

        viewModel.dragMove(legalMoves(viewModel.session.state).first())

        assertEquals(DealStatus.PLAYED, viewModel.dealProgress[seed]?.status)
        assertEquals(viewModel.session.state.moveCount, viewModel.dealProgress.getValue(seed).moves)
    }

    @Test
    fun `picking a deal before the catalog has loaded does nothing`() {
        val viewModel = FreeCellViewModel(initialSeed = 7L)
        val seed = viewModel.session.state.seed

        viewModel.requestSelectDeal(3)

        assertEquals(seed, viewModel.session.state.seed)
        assertEquals(null, viewModel.dealPickerCount)
    }
}
