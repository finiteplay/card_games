package org.finiteplay.spider.storage

import kotlinx.coroutines.runBlocking
import org.finiteplay.core.storage.FakeDataStores
import org.finiteplay.core.ui.layout.HintTimeout
import org.finiteplay.core.ui.layout.RestReminderInterval
import org.finiteplay.spider.layout.SuitCount
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SpiderSettingsDefaultsTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun `a new install defaults to one suit`() = runBlocking {
        val store = SpiderSettingsStore(folder.newFolder(), dataStoreFactory = FakeDataStores::create)
        assertEquals(SuitCount.ONE, store.current().nextSuitCount)
    }

    @Test
    fun `a chosen suit count survives a restart`() = runBlocking {
        val dir = folder.newFolder()
        SpiderSettingsStore(dir, dataStoreFactory = FakeDataStores::create).setNextSuitCount(SuitCount.FOUR)

        val reopened = SpiderSettingsStore(dir, dataStoreFactory = FakeDataStores::create)
        assertEquals(SuitCount.FOUR, reopened.current().nextSuitCount)
    }

    @Test
    fun `hint-shows-winning-move defaults on and survives a restart`() = runBlocking {
        val dir = folder.newFolder()
        val store = SpiderSettingsStore(dir, dataStoreFactory = FakeDataStores::create)
        assertEquals(true, store.current().hintShowsWinningMove)

        store.setHintShowsWinningMove(false)

        val reopened = SpiderSettingsStore(dir, dataStoreFactory = FakeDataStores::create)
        assertEquals(false, reopened.current().hintShowsWinningMove)
    }

    @Test
    fun `hint timeout defaults to 5 seconds and survives a restart`() = runBlocking {
        val dir = folder.newFolder()
        val store = SpiderSettingsStore(dir, dataStoreFactory = FakeDataStores::create)
        assertEquals(HintTimeout.FIVE, store.current().hintTimeout)

        store.setHintTimeout(HintTimeout.TWELVE)

        val reopened = SpiderSettingsStore(dir, dataStoreFactory = FakeDataStores::create)
        assertEquals(HintTimeout.TWELVE, reopened.current().hintTimeout)
    }

    @Test
    fun `rest reminder interval defaults to 60 minutes and survives a restart`() = runBlocking {
        val dir = folder.newFolder()
        val store = SpiderSettingsStore(dir, dataStoreFactory = FakeDataStores::create)
        assertEquals(RestReminderInterval.SIXTY, store.current().restReminderInterval)

        store.setRestReminderInterval(RestReminderInterval.NEVER)

        val reopened = SpiderSettingsStore(dir, dataStoreFactory = FakeDataStores::create)
        assertEquals(RestReminderInterval.NEVER, reopened.current().restReminderInterval)
    }
}
