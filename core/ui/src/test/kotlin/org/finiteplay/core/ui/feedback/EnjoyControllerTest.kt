package org.finiteplay.core.ui.feedback

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.finiteplay.core.session.ENJOY_MIN_GOOD_RESULTS
import org.finiteplay.core.session.ENJOY_RETRY_DAYS
import org.finiteplay.core.storage.EngagementStore
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.core.storage.seedVeteran
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EnjoyControllerTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val day = TimeUnit.DAYS.toMillis(1)
    private var now = 1_000_000_000_000L

    private fun TestScope.controller(dir: java.io.File): Pair<EnjoyController, EngagementStore> {
        val store = EngagementStore(dir, dataStoreFactory = FakeDataStores::create)
        val scope = kotlinx.coroutines.CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return EnjoyController(store, scope) { now } to store
    }

    /** Ten sessions on separate days, then [goodResults] good results: the player is just short of the question. */
    private suspend fun EngagementStore.veteran(goodResults: Int) {
        now = seedVeteran(now, goodResults)
    }

    @Test
    fun `the question opens when the player moves on from a good result they are ready for`() = runTest {
        val (controller, store) = controller(folder.newFolder())
        store.veteran(goodResults = ENJOY_MIN_GOOD_RESULTS - 1)

        controller.goodResult()
        assertFalse("not before moving on", controller.visible)
        controller.moveOn()

        assertTrue(controller.visible)
    }

    @Test
    fun `a player who is not ready is not asked`() = runTest {
        val (controller, store) = controller(folder.newFolder())
        store.veteran(goodResults = 0)

        controller.goodResult()
        controller.moveOn()

        assertFalse(controller.visible)
    }

    @Test
    fun `moving on with no good result pending asks nothing`() = runTest {
        val (controller, store) = controller(folder.newFolder())
        store.veteran(goodResults = ENJOY_MIN_GOOD_RESULTS)

        controller.moveOn()

        assertFalse(controller.visible)
    }

    @Test
    fun `answering is final`() = runTest {
        val (controller, store) = controller(folder.newFolder())
        store.veteran(goodResults = ENJOY_MIN_GOOD_RESULTS - 1)
        controller.goodResult()
        controller.moveOn()

        controller.finished(answered = true)

        assertFalse(controller.visible)
        assertTrue(store.current().answered)
        now += 1000 * day
        controller.goodResult()
        controller.moveOn()
        assertFalse(controller.visible)
    }

    @Test
    fun `dismissing is not an answer and the question returns after thirty days`() = runTest {
        val (controller, store) = controller(folder.newFolder())
        store.veteran(goodResults = ENJOY_MIN_GOOD_RESULTS - 1)
        controller.goodResult()
        controller.moveOn()
        assertTrue(controller.visible)

        controller.finished(answered = false)

        assertFalse(store.current().answered)
        now += day
        controller.goodResult()
        controller.moveOn()
        assertFalse("not again the next day", controller.visible)

        now += ENJOY_RETRY_DAYS * day
        controller.goodResult()
        controller.moveOn()
        assertTrue("again after thirty days", controller.visible)
    }

    @Test
    fun `the question is not opened twice for one result`() = runTest {
        val (controller, store) = controller(folder.newFolder())
        store.veteran(goodResults = ENJOY_MIN_GOOD_RESULTS - 1)
        controller.goodResult()
        controller.moveOn()
        controller.finished(answered = false)

        controller.moveOn()

        assertFalse(controller.visible)
    }

    @Test
    fun `starting the app counts a session`() = runTest {
        val (controller, store) = controller(folder.newFolder())

        controller.appStarted()

        assertEquals(1, store.current().sessions)
    }
}
