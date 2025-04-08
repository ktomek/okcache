package com.github.ktomek.okcache.interceptor.processor

import com.github.ktomek.okcache.HEADER_CACHE_CONTROL
import com.github.ktomek.okcache.NetworkInfoProvider
import com.github.ktomek.okcache.exception.DeviceOfflineException
import io.mockk.every
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

// Expected Cache-Control header value using CacheControl.Builder().noCache()
private val NO_CACHED_CACHE_CONTROL: String = CacheControl.Builder().noCache().build().toString()

@ExtendWith(MockKExtension::class)
internal class NetworkOnlyStrategyProcessorTest {

    @RelaxedMockK
    lateinit var networkInfoProvider: NetworkInfoProvider

    @RelaxedMockK
    lateinit var chain: Interceptor.Chain

    // "cut" stands for Class Under Test.
    private lateinit var cut: NetworkOnlyStrategyProcessor

    @BeforeEach
    fun setUp() {
        cut = NetworkOnlyStrategyProcessor(networkInfoProvider)
    }

    @Test
    fun `GIVEN network is available WHEN invoking processor THEN returns network response`() {
        // GIVEN: network is available
        every { networkInfoProvider.isNetworkAvailable() } returns true

        val request = Request.Builder().url(DUMMY_URL).build()
        every { chain.request() } returns request

        // Stub chain.proceed(request) to return a network response with the expected header.
        val networkResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Network response".toResponseBody(null))
            .build()
        every {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == NO_CACHED_CACHE_CONTROL
                }
            )
        } returns networkResponse

        // WHEN: invoking the processor
        val response = cut.invoke(chain, request)

        // THEN: returns the network response
        assertEquals("Network response", response.body?.string())
        verify(exactly = 1) {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == NO_CACHED_CACHE_CONTROL
                }
            )
        }
    }

    @Test
    fun `GIVEN network is not available WHEN invoking processor THEN throw DeviceOfflineException`() {
        // GIVEN: network is not available
        every { networkInfoProvider.isNetworkAvailable() } returns false

        val request = Request.Builder().url(DUMMY_URL).build()
        every { chain.request() } returns request

        // WHEN & THEN: invoking the processor throws a DeviceOfflineException
        val exception = assertThrows(DeviceOfflineException::class.java) {
            cut.invoke(chain, request)
        }
        assertEquals("No network available for NETWORK_ONLY strategy", exception.message)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    private companion object {
        const val DUMMY_URL = "http://example.com/"
    }
}
