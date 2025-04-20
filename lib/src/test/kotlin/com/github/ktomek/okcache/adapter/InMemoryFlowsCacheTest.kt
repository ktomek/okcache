package com.github.ktomek.okcache.adapter

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class InMemoryFlowsCacheTest {

    private val cache = InMemoryFlowsCache()

    @Test
    fun `GIVEN a request WHEN getFlow is called THEN it returns a new flow`() = runTest {
        val request = Request.Builder().url("http://example.com").build()

        cache.getFlow<String>(request).test {
            ensureAllEventsConsumed()
        }
    }

    @Test
    fun `GIVEN a request and an emitted value WHEN getFlow and emit are called THEN flow emits the correct value`() =
        runTest {
            val request = Request.Builder().url("http://example.com").build()
            val expectedValue = "Test Value"

            cache.getFlow<String>(request).test {
                cache.emit(request, expectedValue)
                assertEquals(expectedValue, awaitItem())
            }
        }

    @Test
    fun `GIVEN the same request WHEN getFlow is called multiple times THEN it returns the same flow`() {
        val request = Request.Builder().url("http://example.com").build()

        val flow1 = cache.getFlow<String>(request)
        val flow2 = cache.getFlow<String>(request)

        assertSame(flow1, flow2)
    }
}
