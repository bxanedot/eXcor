package com.excor

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class DefaultHostTest {
    @Test
    fun defaultHostGivesASignedOutSessionAndAWorkingStore() = runBlocking {
        val logged = mutableListOf<String>()
        val host = excorDefaultHost(region = "fr", language = "fr", log = { _, m -> logged += m })
        assertEquals(ExcorSession(null, null, "FR", "fr"), host.session())
        assertNull(host.configStore.getString("k"))
        host.configStore.putString("k", "v")
        host.configStore.putLong("n", 3)
        assertEquals("v", host.configStore.getString("k"))
        assertEquals(3L, host.configStore.getLong("n"))
        host.log(ExcorLogLevel.INFO, "hello", null)
        assertEquals(listOf("hello"), logged)
        assertFailsWith<IllegalArgumentException> { excorDefaultHost(region = "USA") }
        assertFailsWith<IllegalArgumentException> { excorDefaultHost(language = "en;x") }
        val library = Excor(host) // constructs without further setup
        library.close()
        host.httpClient.connectionPool.evictAll()
        host.httpClient.dispatcher.executorService.shutdown()
        Unit
    }
}
