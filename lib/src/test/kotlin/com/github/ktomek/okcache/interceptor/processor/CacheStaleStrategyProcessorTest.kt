package com.github.ktomek.okcache.interceptor.processor

import com.github.ktomek.okcache.GATEWAY_TIMEOUT_CODE
import com.github.ktomek.okcache.HEADER_CACHE_CONTROL
import com.github.ktomek.okcache.exception.NoCachedResponseException
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.invoke

internal class CacheStaleStrategyProcessorTest {

    private val address = "http://example.com/"
    private val mockInterceptorChain: Interceptor.Chain = mockk(relaxed = true)
    private lateinit var cut: CacheStaleStrategyProcessor

    @BeforeEach
    fun setUp() {
        cut = CacheStaleStrategyProcessor
    }

    @Test
    fun `GIVEN cache is available WHEN invoking processor THEN return cached response`() {
        val mockResponse: Response = mockk(relaxed = true) {
            every { code } returns 200
        }

        val request = Request.Builder().url(address).build()
        every { mockInterceptorChain.request() } returns request
        every { mockInterceptorChain.proceed(any()) } returns mockResponse

        val response = cut.invoke(mockInterceptorChain, request)

        assertEquals(mockResponse, response)
        verify(exactly = 1) {
            mockInterceptorChain.proceed(
                match {
                    it.url.toString() == address &&
                        it.header(HEADER_CACHE_CONTROL) == CacheControl.FORCE_CACHE.toString()
                }
            )
        }
    }

    @Test
    fun `GIVEN cache is missing WHEN invoking processor THEN throw NoCachedResponseException`() {
        val request = Request.Builder().url(address).build()
        val timeoutResponse: Response = mockk(relaxed = true) {
            every { code } returns GATEWAY_TIMEOUT_CODE
        }
        every { mockInterceptorChain.request() } returns request
        every { mockInterceptorChain.proceed(any()) } returns timeoutResponse

        val exception = assertThrows(NoCachedResponseException::class.java) {
            cut.invoke(mockInterceptorChain, request)
        }
        assertEquals("No cached response available for CACHE_ONLY strategy", exception.message)
        verify(exactly = 1) {
            mockInterceptorChain.proceed(
                match {
                    it.url.toString() == address &&
                        it.header(HEADER_CACHE_CONTROL) == CacheControl.FORCE_CACHE.toString()
                }
            )
        }
    }
}
