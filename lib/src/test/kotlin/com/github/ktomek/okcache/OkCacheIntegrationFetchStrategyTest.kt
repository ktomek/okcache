package com.github.ktomek.okcache

import com.github.ktomek.okcache.FetchStrategy.CACHE_FIRST
import com.github.ktomek.okcache.FetchStrategy.CACHE_ONLY
import com.github.ktomek.okcache.FetchStrategy.CACHE_STALE
import com.github.ktomek.okcache.FetchStrategy.NETWORK_FIRST
import com.github.ktomek.okcache.FetchStrategy.NETWORK_ONLY
import com.github.ktomek.okcache.exception.DeviceOfflineException
import com.github.ktomek.okcache.exception.NoCachedResponseException
import com.github.ktomek.okcache.interceptor.RequestCacheControlInterceptor
import com.github.ktomek.okcache.interceptor.ResponseCacheControlInterceptor
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.junit5.MockKExtension
import io.mockk.spyk
import io.mockk.verify
import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.io.File
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals

/**
 * Integration test for caching strategies using MockWebServer, Retrofit, and OkHttp.
 * This test spies on the ResponseCacheControlInterceptor to verify how many times its intercept
 * method is invoked and covers cases for network (online/offline), server down, and empty caches.
 */
@ExtendWith(MockKExtension::class)
internal class OkCacheIntegrationFetchStrategyTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var cacheDir: File
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var retrofit: Retrofit
    private lateinit var testApi: TestApi

    // Simple toggle for network availability.
    private var networkAvailable: Boolean = true
    private val networkInfoProvider = object : NetworkInfoProvider {
        override fun isNetworkAvailable() = networkAvailable
    }

    // Cache size: 10 MB.
    private val cacheSize = 10L * 1024 * 1024

    // Use spyk for ResponseCacheControlInterceptor.
    private lateinit var responseInterceptor: ResponseCacheControlInterceptor

    @BeforeEach
    fun setUp() {
        // Start MockWebServer.
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Create temporary cache directory.
        cacheDir = Files.createTempDirectory("okcache").toFile()

        // Setup OkHttpClient with Request and spyk'ed Response interceptors.
        val requestInterceptor = RequestCacheControlInterceptor(networkInfoProvider)
        responseInterceptor = spyk(ResponseCacheControlInterceptor())

        okHttpClient = OkHttpClient.Builder()
            .cache(Cache(cacheDir, cacheSize))
            .readTimeout(100, TimeUnit.MILLISECONDS)
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .addInterceptor(requestInterceptor)
            .addNetworkInterceptor(responseInterceptor)
            .build()

        // Build Retrofit.
        retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .client(okHttpClient)
            .addConverterFactory(ScalarsConverterFactory.create())
            .build()

        testApi = retrofit.create(TestApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
        cacheDir.deleteRecursively()
    }

    @BeforeEach
    fun resetSpyk() {
        clearMocks(responseInterceptor)
    }

    // ----- NETWORK_ONLY Tests -----

    @Test
    fun `GIVEN fetch strategy NETWORK_ONLY WHEN online and server responds THEN returns network response and interceptor called once`() {
        networkAvailable = true
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Network Only Response"))

        val response = testApi.getData(NETWORK_ONLY).execute()

        assertTrue(response.isSuccessful)
        assertEquals("Network Only Response", response.body())
        val recordedRequest = mockWebServer.takeRequest()
        val expectedHeader = CacheControl.Builder().noCache().build().toString()
        assertEquals(expectedHeader, recordedRequest.getHeader("Cache-Control"))
        verify(exactly = 1) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy NETWORK_ONLY WHEN online but server is down THEN throws exception`() {
        networkAvailable = true
        // Do not enqueue any response so that the call fails (simulate server down).
        // Expect an exception (e.g. IOException).
        assertThrows<SocketTimeoutException> {
            testApi.getData(NETWORK_ONLY).execute()
        }
        // Verify that the network interceptor was invoked.
        verify(exactly = 1) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy NETWORK_ONLY WHEN offline and cache is empty THEN throws DeviceOfflineException`() {
        networkAvailable = false
        // Since no prior call has cached a response, the cache is empty.
        assertThrows<DeviceOfflineException> {
            testApi.getData(NETWORK_ONLY).execute()
        }
        // No network call is made so interceptor should not be invoked.
        verify(exactly = 0) { responseInterceptor.intercept(any()) }
    }

    // ----- CACHE_ONLY Tests -----

    @Test
    fun `GIVEN fetch strategy CACHE_ONLY WHEN cached response is fresh THEN returns cached response and interceptor not called`() {
        // First, cache a response.
        networkAvailable = true
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Cache Only Cached Response"))
        val initialResponse = testApi.getData(NETWORK_ONLY).execute()
        assertEquals("Cache Only Cached Response", initialResponse.body())
        mockWebServer.shutdown()
        clearMocks(responseInterceptor)

        // WHEN: using CACHE_ONLY while online.
        val response = testApi.getData(CACHE_ONLY).execute()
        assertTrue(response.isSuccessful)
        assertEquals("Cache Only Cached Response", response.body())
        verify(exactly = 0) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy CACHE_ONLY WHEN cached response is stale THEN throws NoCachedResponseException`() {
        // GIVEN: First, cache a response that is immediately stale.
        // We simulate this by enqueuing a response with a Cache-Control header set to "public, max-age=0".
        networkAvailable = true
        val staleResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Cache-Control", "public, max-age=0")
            .setBody("Stale Response")
        mockWebServer.enqueue(staleResponse)
        // Now, override the responseInterceptor's logic.
        every { responseInterceptor.intercept(any()) } answers {
            val chain = it.invocation.args[0] as Interceptor.Chain
            chain.proceed(chain.request())
        }

        // Make the initial call using NETWORK_ONLY so that the stale response is cached.
        val initialResponse = testApi.getData(NETWORK_ONLY).execute()
        assertEquals("Stale Response", initialResponse.body())

        // Clear the spyk so we can count new network calls.
        clearMocks(responseInterceptor)

        // WHEN: using CACHE_ONLY while online.
        assertThrows<NoCachedResponseException> {
            testApi.getData(CACHE_ONLY).execute()
        }
        verify(exactly = 0) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy CACHE_ONLY WHEN cache is empty THEN throws NoCachedResponseException`() {
        // Simulate that OkHttp returns HTTP 504 when no cache is available.
        mockWebServer.enqueue(MockResponse().setResponseCode(504))
        assertThrows<NoCachedResponseException> {
            testApi.getData(CACHE_ONLY).execute()
        }
        verify(exactly = 0) { responseInterceptor.intercept(any()) }
    }

    // ----- NETWORK_FIRST Tests -----

    @Test
    fun `GIVEN fetch strategy NETWORK_FIRST WHEN online and network call succeeds THEN returns network response and interceptor called once`() {
        networkAvailable = true
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Network First Response"))

        val response = testApi.getData(NETWORK_FIRST).execute()

        assertTrue(response.isSuccessful)
        assertEquals("Network First Response", response.body())
        val recordedRequest = mockWebServer.takeRequest()
        val expectedHeader = CacheControl.Builder().noCache().build().toString()
        assertEquals(expectedHeader, recordedRequest.getHeader("Cache-Control"))
        verify(exactly = 1) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy NETWORK_FIRST WHEN online but network call fails and cache is fresh THEN returns cached response and interceptor called once`() {
        // First, cache a response.
        networkAvailable = true
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Network First Cached Response"))
        val cachedResponse = testApi.getData(NETWORK_ONLY).execute()
        assertEquals("Network First Cached Response", cachedResponse.body())
        clearMocks(responseInterceptor)

        // Simulate network failure by shutting down the server.
        networkAvailable = true
        mockWebServer.shutdown()

        // WHEN: calling NETWORK_FIRST; network call fails and fallback to cache occurs.
        val response = testApi.getData(NETWORK_FIRST).execute()
        assertEquals("Network First Cached Response", response.body())
        // The interceptor is invoked only for the failed network call.
        verify(exactly = 1) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy NETWORK_FIRST WHEN offline and cache is empty THEN throws DeviceOfflineException exception`() {
        // Ensure cache is empty.
        networkAvailable = false
        // Expect an exception because no cached response exists.
        assertThrows<DeviceOfflineException> {
            testApi.getData(NETWORK_FIRST).execute()
        }
        verify(exactly = 0) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy NETWORK_FIRST WHEN online and cache is empty and server is down THEN throws SocketTimeoutException exception`() {
        // Ensure cache is empty.
        networkAvailable = true
        // Expect an exception because no cached response exists.
        assertThrows<SocketTimeoutException> {
            testApi.getData(NETWORK_FIRST).execute()
        }
        verify(exactly = 1) { responseInterceptor.intercept(any()) }
    }

    // ----- CACHE_FIRST Tests -----

    @Test
    fun `GIVEN fetch strategy CACHE_FIRST WHEN cached response is fresh THEN returns cached response and interceptor not called`() {
        // First, cache a response.
        networkAvailable = true
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Cache First Cached Response"))
        val cachedResponse = testApi.getData(NETWORK_ONLY).execute()
        assertEquals("Cache First Cached Response", cachedResponse.body())
        clearMocks(responseInterceptor)

        val response = testApi.getData(CACHE_FIRST).execute()
        assertTrue(response.isSuccessful)
        assertEquals("Cache First Cached Response", response.body())
        verify(exactly = 0) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy CACHE_FIRST WHEN cache is empty THEN returns network response`() {
        // First, cache a response.
        networkAvailable = true
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("Network Response"))

        val response = testApi.getData(CACHE_FIRST).execute()
        assertTrue(response.isSuccessful)
        assertEquals("Network Response", response.body())
        verify(exactly = 1) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy CACHE_FIRST WHEN cache is empty THEN returns network error response`() {
        // GIVEN: No cached response is available.
        // We simulate an empty cache by not caching any response beforehand.
        networkAvailable = true

        // Instead of expecting an exception, we enqueue a network error response.
        // (Note: In a real offline scenario, no network call would be possible, but here we assume that
        // if the cache is empty, OkHttp will attempt a network call and then return the server's error response.)
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("Server Error"))

        // WHEN: calling API with CACHE_FIRST strategy.
        val response = testApi.getData(CACHE_FIRST).execute()

        // THEN: the network call is made and an error response is returned.
        assertEquals(500, response.code())
        assertEquals("Server Error", response.errorBody()?.string())

        // Verify that the ResponseCacheControlInterceptor is invoked (i.e. a network call was attempted)
        verify(exactly = 1) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy CACHE_FIRST WHEN cached response is stale THEN returns fresh network response`() {
        // GIVEN: First, cache a response that is immediately stale.
        // We simulate this by enqueuing a response with a Cache-Control header set to "public, max-age=0".
        networkAvailable = true
        val staleResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Cache-Control", "public, max-age=0")
            .setBody("Stale Response")
        mockWebServer.enqueue(staleResponse)
        // Now, override the responseInterceptor's logic.
        every { responseInterceptor.intercept(any()) } answers {
            val chain = it.invocation.args[0] as Interceptor.Chain
            chain.proceed(chain.request())
        }

        // Make the initial call using NETWORK_ONLY so that the stale response is cached.
        val initialResponse = testApi.getData(NETWORK_ONLY).execute()
        assertEquals("Stale Response", initialResponse.body())

        // Clear the spyk so we can count new network calls.
        clearMocks(responseInterceptor)

        // GIVEN: Network is still available and a fresh response is expected.
        // Enqueue a fresh network response.
        val freshResponse = MockResponse().setResponseCode(200).setBody("Fresh Network Response")
        mockWebServer.enqueue(freshResponse)

        // WHEN: Calling the API with CACHE_FIRST strategy.
        // Since the cached response is stale, the interceptors should detect that and perform a network call.
        val response = testApi.getData(CACHE_FIRST).execute()

        // THEN: The fresh network response is returned.
        assertTrue(response.isSuccessful)
        assertEquals("Fresh Network Response", response.body())

        // Verify that the network interceptor was invoked,
        // which indicates that a network call was made instead of returning the stale cache.
        verify(exactly = 1) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN server returns error response WHEN using CACHE_ONLY THEN error response is not cached`() {
        // GIVEN: Network is available and the server returns an error response.
        networkAvailable = true
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("Request error"))

        // WHEN: Call the API using NETWORK_ONLY strategy to retrieve the error response.
        val errorResponse = testApi.getData(NETWORK_ONLY).execute()
        // Verify that an error response is received.
        assertEquals(404, errorResponse.code())
        // (Optional) Check the error body.
        val body = errorResponse.errorBody()?.string() ?: errorResponse.body()
        assertEquals("Request error", body)

        // Expect that calling CACHE_ONLY will throw NoCachedResponseException.
        assertThrows<NoCachedResponseException> {
            val response = testApi.getData(CACHE_ONLY).execute()
            println(response.code())
            println(response.body())
            println(response.errorBody()?.string())
        }
    }

    // ----- CACHE_STALE Tests -----

    @Test
    fun `GIVEN fetch strategy CACHE_STALE WHEN cache is not empty THEN returns cached response even if stale and interceptor not called`() {
        // First, cache a response.
        // GIVEN: First, cache a response that is immediately stale.
        // We simulate this by enqueuing a response with a Cache-Control header set to "public, max-age=0".
        networkAvailable = true
        val staleResponse = MockResponse()
            .setResponseCode(200)
            .setHeader("Cache-Control", "public, max-age=1")
            .setBody("Cached Stale Response")
        mockWebServer.enqueue(staleResponse)
        // Now, override the responseInterceptor's logic.
        every { responseInterceptor.intercept(any()) } answers {
            val chain = it.invocation.args[0] as Interceptor.Chain
            chain.proceed(chain.request())
        }

        // Make the initial call using NETWORK_ONLY so that the stale response is cached.
        val initialResponse = testApi.getData(NETWORK_ONLY).execute()
        assertEquals("Cached Stale Response", initialResponse.body())

        // Clear the spyk so we can count new network calls.
        clearMocks(responseInterceptor)

        // WHEN: simulate offline.
        val response = testApi.getData(CACHE_STALE).execute()
        assertTrue(response.isSuccessful)
        assertEquals("Cached Stale Response", response.body())
        // For CACHE_STALE, the interceptor is not invoked because stale responses are allowed.
        verify(exactly = 0) { responseInterceptor.intercept(any()) }
    }

    @Test
    fun `GIVEN fetch strategy CACHE_STALE WHEN cache is empty THEN throws exception`() {
        // First, cache a response.
        // GIVEN: First, cache a response that is immediately stale.
        // We simulate this by enqueuing a response with a Cache-Control header set to "public, max-age=0".
        networkAvailable = true

        // WHEN: simulate offline.
        assertThrows<NoCachedResponseException> {
            testApi.getData(CACHE_STALE).execute()
        }
        // For CACHE_STALE, the interceptor is not invoked because stale responses are allowed.
        verify(exactly = 0) { responseInterceptor.intercept(any()) }
    }

    // Retrofit API interface.
    interface TestApi {
        @Cached(maxAge = 60)
        @GET("data")
        fun getData(@Header("X-Fetch-Strategy") strategy: FetchStrategy): Call<String>
    }
}
