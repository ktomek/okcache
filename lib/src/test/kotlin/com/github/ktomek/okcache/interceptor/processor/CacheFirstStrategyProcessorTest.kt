@file:Suppress("MaxLineLength")

package com.github.ktomek.okcache.interceptor.processor

import com.github.ktomek.okcache.Cached
import com.github.ktomek.okcache.HEADER_CACHE_CONTROL
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.mockk
import io.mockk.verify
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import retrofit2.Invocation
import java.lang.IllegalStateException
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.invoke

@ExtendWith(MockKExtension::class)
internal class CacheFirstStrategyProcessorTest {

    @RelaxedMockK
    private lateinit var chain: Interceptor.Chain

    @InjectMockKs
    private lateinit var cut: CacheFirstStrategyProcessor

    @Test
    fun `GIVEN cache is available WHEN invoking processor THEN return cached response`() {
        val mockResponse: Response = mockk(relaxed = true) {
            every { code } returns 200
        }
        val method: Method = DummyApi::class.java.getMethod("dummyMethod")
        checkNotNull(method.getAnnotation(Cached::class.java)) {
            "cacheMethod should be annotated with @Cached"
        }

        // Build an Invocation that returns the annotated method.
        val invocation: Invocation = Invocation.of(method, emptyList<Any>())

        // Build a request with the Invocation tag.
        val request = Request.Builder()
            .url(DUMMY_URL)
            .tag(Invocation::class.java, invocation)
            .build()

        every { chain.request() } returns request
        every { chain.proceed(any()) } returns mockResponse

        val response = cut(chain, request)

        assertEquals(mockResponse, response)
        verify(exactly = 1) {
            chain.proceed(
                match {
                    it.url.toString() == DUMMY_URL &&
                        it.header(HEADER_CACHE_CONTROL) == CacheControl.Builder().maxAge(60, SECONDS).build().toString()
                }
            )
        }
    }

    @Test
    fun `GIVEN Cache annotation is missing WHEN invoking processor THEN throw IllegalStateException`() {
        val method: Method = DummyApi::class.java.getMethod("noCacheDummyMethod")

        // Build an Invocation that returns the annotated method.
        val invocation: Invocation = Invocation.of(method, emptyList<Any>())

        // Build a request with the Invocation tag.
        val request = Request.Builder()
            .url(DUMMY_URL)
            .tag(Invocation::class.java, invocation)
            .build()

        val exception = assertThrows(IllegalStateException::class.java) {
            cut.invoke(chain, request)
        }
        assertEquals("Method should be annotated with @Cached", exception.message)
        verify(exactly = 0) { chain.proceed(any()) }
    }

    private companion object {
        const val DUMMY_URL = "http://example.com/"
    }

    interface DummyApi {
        @Cached(maxAge = 60)
        fun dummyMethod()

        fun noCacheDummyMethod()
    }
}
