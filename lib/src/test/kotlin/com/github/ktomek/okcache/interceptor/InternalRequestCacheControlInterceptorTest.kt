package com.github.ktomek.okcache.interceptor

import com.github.ktomek.okcache.Cached
import com.github.ktomek.okcache.FetchStrategy
import com.github.ktomek.okcache.HEADER_FETCH_STRATEGY
import com.github.ktomek.okcache.NetworkInfoProvider
import com.github.ktomek.okcache.interceptor.cache.AnnotationCache
import com.github.ktomek.okcache.interceptor.processor.FetchStrategyProcessorMapper
import com.github.ktomek.okcache.interceptor.processor.NetworkStrategyProcessor
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.RelaxedMockK
import io.mockk.junit5.MockKExtension
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import retrofit2.Invocation
import java.lang.reflect.Method

@ExtendWith(MockKExtension::class)
internal class InternalRequestCacheControlInterceptorTest {

    // Dummy interface to simulate a method annotated with @Cached.
    interface DummyApi {
        @Cached(maxAge = 60)
        fun cacheMethod()

        fun noCacheMethod()
    }

    private val dummyUrl = "http://example.com/"

    @RelaxedMockK
    lateinit var chain: Interceptor.Chain

    @RelaxedMockK
    lateinit var networkInfoProvider: NetworkInfoProvider

    @RelaxedMockK
    lateinit var annotationCache: AnnotationCache

    @RelaxedMockK
    lateinit var fetchStrategyProcessorMapper: FetchStrategyProcessorMapper

    @RelaxedMockK
    lateinit var fetchStrategyProcessor: NetworkStrategyProcessor

    // "cut" stands for Class Under Test.
    @InjectMockKs
    private lateinit var cut: InternalRequestCacheControlInterceptor

    @BeforeEach
    fun setUp() {
        every { networkInfoProvider.isNetworkAvailable() } returns true
        every { fetchStrategyProcessorMapper.invoke((any())) } returns fetchStrategyProcessor
    }

    @Test
    fun `GIVEN request without Cached annotation WHEN intercept THEN proceed with original request`() {
        // Create a request that lacks a @Cached annotation.
        val request = Request.Builder().url(dummyUrl).build()

        // Simulate that the request's Invocation tag returns a method without a @Cached annotation.
        val noCacheMethod: Method =
            DummyApi::class.java.getMethod("noCacheMethod") // toString is not annotated with @Cached
        val invocation: Invocation = Invocation.of(noCacheMethod, emptyList<Any>())
        // Attach the Invocation tag to the request.
        val requestWithInvocation = request.newBuilder()
            .tag(Invocation::class.java, invocation)
            .build()

        every { chain.request() } returns requestWithInvocation

        // Configure the annotation cache to return null (i.e. not annotated).
        every { annotationCache.getAnnotation(noCacheMethod, Cached::class.java) } returns null

        val originalResponse = Response.Builder()
            .request(requestWithInvocation)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("Original response".toResponseBody(null))
            .build()
        every { chain.proceed(requestWithInvocation) } returns originalResponse

        val response = cut.intercept(chain)
        assertEquals("Original response", response.body?.string())

        verify { chain.proceed(requestWithInvocation) }
        verify(exactly = 0) { fetchStrategyProcessorMapper.invoke(any()) }
    }

    @Test
    fun `GIVEN request with Cached annotation and not fetch strategy THEN invoke cacheFirstInterceptor`() {
        // Create a dummy method annotated with @Cached.
        val cacheMethod: Method = DummyApi::class.java.getMethod("cacheMethod")
        // Ensure that the method indeed has the @Cached annotation.
        val cachedAnnotation: Cached = checkNotNull(cacheMethod.getAnnotation(Cached::class.java)) {
            "cacheMethod should be annotated with @Cached"
        }

        // Create an Invocation that returns the annotated method.
        val invocation: Invocation = Invocation.of(cacheMethod, emptyList<Any>())

        // Build a request with the HEADER_FETCH_STRATEGY set to "NETWORK_ONLY" and attach the Invocation tag.
        val request = Request.Builder()
            .url(dummyUrl)
            .tag(Invocation::class.java, invocation)
            .build()

        every { chain.request() } returns request

        // Configure the annotation cache to return the cachedAnnotation.
        every { annotationCache.getAnnotation(cacheMethod, Cached::class.java) } returns cachedAnnotation

        // Simulate that the networkOnlyInterceptor (our processor for NETWORK_ONLY) returns a dummy response.
        val dummyResponse = getResponse(request)
        every { fetchStrategyProcessor.invoke(chain, any()) } returns dummyResponse

        val response = cut.intercept(chain)
        assertEquals("response", response.body?.string())

        // Verify that the processor was called with a request that no longer contains the fetch strategy header.
        verify {
            fetchStrategyProcessor.invoke(
                chain,
                match {
                    it.url.toString() == dummyUrl && it.header(HEADER_FETCH_STRATEGY) == null
                }
            )
        }
    }

    @ParameterizedTest
    @EnumSource(FetchStrategy::class)
    fun `GIVEN request with Cached annotation and fetch strategy WHEN intercept THEN invoke fetchStrategyProcessor`(
        strategy: FetchStrategy
    ) {
        // Create a dummy method annotated with @Cached.
        val cacheMethod: Method = DummyApi::class.java.getMethod("cacheMethod")
        // Ensure that the method indeed has the @Cached annotation.
        val cachedAnnotation: Cached = checkNotNull(cacheMethod.getAnnotation(Cached::class.java)) {
            "cacheMethod should be annotated with @Cached"
        }

        // Create an Invocation that returns the annotated method.
        val invocation: Invocation = Invocation.of(cacheMethod, emptyList<Any>())

        // Build a request with the HEADER_FETCH_STRATEGY set to "NETWORK_FIRST" and attach the Invocation tag.
        val request = getRequest(invocation, strategy)

        every { chain.request() } returns request

        // Configure the annotation cache to return the cachedAnnotation.
        every {
            annotationCache.getAnnotation(
                cacheMethod,
                Cached::class.java
            )
        } returns cachedAnnotation

        // Simulate that the networkOnlyInterceptor (our processor for CACHE_ONLY) returns a dummy response.
        val dummyResponse = getResponse(request)
        every { fetchStrategyProcessor.invoke(chain, any()) } returns dummyResponse

        val response = cut.intercept(chain)
        assertEquals("response", response.body?.string())

        // Verify that the processor was called with a request that no longer contains the fetch strategy header.
        verify {
            fetchStrategyProcessor.invoke(
                chain,
                match {
                    it.url.toString() == dummyUrl && it.header(HEADER_FETCH_STRATEGY) == null
                }
            )
        }
        verify { fetchStrategyProcessorMapper.invoke(eq(strategy)) }
    }

    private fun getRequest(invocation: Invocation, fetchStrategy: FetchStrategy): Request = Request.Builder()
        .url(dummyUrl)
        .header(HEADER_FETCH_STRATEGY, fetchStrategy.toString())
        .tag(Invocation::class.java, invocation)
        .build()

    private fun getResponse(request: Request): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body("response".toResponseBody(null))
        .build()
}
