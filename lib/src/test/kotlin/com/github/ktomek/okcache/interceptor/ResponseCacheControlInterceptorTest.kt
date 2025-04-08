package com.github.ktomek.okcache.interceptor

import com.github.ktomek.okcache.Cached
import com.github.ktomek.okcache.HEADER_CACHE_CONTROL
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
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
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import retrofit2.Invocation
import java.lang.reflect.Method
import java.util.concurrent.TimeUnit
import kotlin.test.assertNull

@ExtendWith(MockKExtension::class)
internal class ResponseCacheControlInterceptorTest {

    private val dummyUrl = "http://example.com/"

    @RelaxedMockK
    lateinit var chain: Interceptor.Chain

    // "cut" stands for class under test.
    @InjectMockKs
    private lateinit var cut: ResponseCacheControlInterceptor

    // Dummy API to obtain a method annotated with @Cached.
    interface DummyApi {
        @Cached(maxAge = 60)
        fun dummyMethod()
    }

    @Test
    fun `GIVEN request with Cached annotation WHEN intercept THEN response is modified with expected Cache-Control header`() {
        // GIVEN a method annotated with @Cached
        val method: Method = DummyApi::class.java.getMethod("dummyMethod")
        check(method.getAnnotation(Cached::class.java) != null) { "Method should be annotated with @Cached" }

        // Build an Invocation that returns the annotated method.
        val invocation: Invocation = Invocation.of(method, emptyList<Any>())

        // Build a request with the Invocation tag.
        val request = Request.Builder()
            .url(dummyUrl)
            .tag(Invocation::class.java, invocation)
            .build()

        every { chain.request() } returns request

        // Base response from chain.proceed(request)
        val baseResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Original response".toResponseBody(null))
            .build()

        every { chain.proceed(request) } returns baseResponse

        // Expected Cache-Control header built from @Cached(maxAge=60)
        val expectedCacheControl = CacheControl.Builder().maxAge(60, TimeUnit.SECONDS).build().toString()

        // WHEN intercept is called
        val response = cut.intercept(chain)

        // THEN the response is modified with the expected Cache-Control header
        assertEquals("Original response", response.body?.string())
        assertEquals(expectedCacheControl, response.header(HEADER_CACHE_CONTROL))
        verify { chain.proceed(request) }
    }

    @Test
    fun `GIVEN request without Cached annotation WHEN intercept THEN proceeds with original response`() {
        // GIVEN a request that does not have an Invocation tag (or its method is not annotated)
        val request = Request.Builder().url(dummyUrl).build()
        every { chain.request() } returns request

        val baseResponse = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Original response".toResponseBody(null))
            .build()

        every { chain.proceed(request) } returns baseResponse

        // WHEN intercept is called
        val response = cut.intercept(chain)

        // THEN the response is the original response (no modification applied)
        assertEquals("Original response", response.body?.string())
        assertNull(response.header(HEADER_CACHE_CONTROL))
        verify { chain.proceed(request) }
    }
}
