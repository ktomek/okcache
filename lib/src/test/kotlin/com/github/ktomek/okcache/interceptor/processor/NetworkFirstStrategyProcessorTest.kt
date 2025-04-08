package com.github.ktomek.okcache.interceptor.processor

import com.github.ktomek.okcache.HEADER_CACHE_CONTROL
import com.github.ktomek.okcache.NetworkInfoProvider
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import okhttp3.CacheControl
import okhttp3.CacheControl.Companion.FORCE_NETWORK
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import java.io.IOException
import kotlin.invoke

@ExtendWith(MockKExtension::class)
internal class NetworkFirstStrategyProcessorTest {

    @RelaxedMockK
    lateinit var networkInfoProvider: NetworkInfoProvider

    @RelaxedMockK
    lateinit var chain: Interceptor.Chain

    @InjectMockKs
    private lateinit var cut: NetworkFirstStrategyProcessor

    @Test
    fun `GIVEN network is available WHEN network call succeeds THEN returns network response`() {
        // GIVEN network is available
        every { networkInfoProvider.isNetworkAvailable() } returns true

        val request = Request.Builder().url(DUMMY_URL).build()
        every { chain.request() } returns request

        // Stub chain.proceed(request) with a request having the no-cache header
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
                        it.header(HEADER_CACHE_CONTROL) == FORCE_NETWORK.toString().toString()
                }
            )
        } returns networkResponse

        // WHEN invoking the processor
        val response = cut.invoke(chain, request)

        // THEN returns network response
        assertEquals("Network response", response.body?.string())

        verify(exactly = 1) {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == FORCE_NETWORK.toString()
                }
            )
        }
    }

    @Test
    fun `GIVEN network is available WHEN network call fails THEN falls back to cache and returns cached response`() {
        // GIVEN network is available
        every { networkInfoProvider.isNetworkAvailable() } returns true

        val request = Request.Builder().url(DUMMY_URL).build()
        every { chain.request() } returns request

        // Simulate network call failure for no-cache header
        every {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == FORCE_NETWORK.toString()
                }
            )
        } throws IOException("Network failure")

        // Stub fallback: chain.proceed with cache-only header returns cached response
        val cachedResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Cached response".toResponseBody(null))
            .build()

        every {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == CacheControl.Builder().onlyIfCached().build().toString()
                }
            )
        } returns cachedResponse

        // WHEN invoking the processor
        val response = cut.invoke(chain, request)
        // THEN returns cached response
        assertEquals("Cached response", response.body?.string())

        verify(exactly = 1) {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == FORCE_NETWORK.toString()
                }
            )
        }
        verify(exactly = 1) {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == CacheControl.Builder().onlyIfCached().build().toString()
                }
            )
        }
    }

    @Test
    fun `GIVEN network is not available WHEN invoking processor THEN falls back to cache-only and returns cached response`() {
        // GIVEN network is not available
        every { networkInfoProvider.isNetworkAvailable() } returns false

        val request = Request.Builder().url(DUMMY_URL).build()
        every { chain.request() } returns request

        val offlineCachedResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Offline cached response".toResponseBody(null))
            .build()

        every {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == CacheControl.Builder().onlyIfCached().build().toString()
                }
            )
        } returns offlineCachedResponse

        // WHEN invoking the processor
        val response = cut.invoke(chain, request)
        // THEN returns cached response
        assertEquals("Offline cached response", response.body?.string())

        verify(exactly = 1) {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == CacheControl.Builder().onlyIfCached().build().toString()
                }
            )
        }
    }

    private companion object {
        const val DUMMY_URL = "http://example.com/"

        // Define expected Cache-Control header values using CacheControl.Builder.
    }
}
