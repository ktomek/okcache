package com.github.ktomek.okcache.adapter

import app.cash.turbine.test
import com.github.ktomek.okcache.Cached
import com.github.ktomek.okcache.FetchStrategy
import com.github.ktomek.okcache.HEADER_FETCH_STRATEGY
import com.github.ktomek.okcache.NetworkInfoProvider
import com.github.ktomek.okcache.interceptor.RequestCacheControlInterceptor
import com.github.ktomek.okcache.interceptor.ResponseCacheControlInterceptor
import io.mockk.spyk
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class FlowCallAdapterFactoryIntegrationTest {

    interface HelloApi {
        @GET("/hello")
        @Cached(maxAge = 60)
        fun getHello(@Header(HEADER_FETCH_STRATEGY) fetchStrategy: FetchStrategy): Flow<String>
    }

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: HelloApi
    private lateinit var responseInterceptor: ResponseCacheControlInterceptor

    private var networkAvailable: Boolean = true
    private val networkInfoProvider = object : NetworkInfoProvider {
        override fun isNetworkAvailable() = networkAvailable
    }

    private val cacheSize = 10L * 1024 * 1024

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val cacheDir = Files.createTempDirectory("okcache").toFile()

        val requestInterceptor = RequestCacheControlInterceptor(networkInfoProvider)
        responseInterceptor = spyk(ResponseCacheControlInterceptor())

        val okHttpClient = OkHttpClient.Builder()
            .cache(Cache(cacheDir, cacheSize))
            .readTimeout(100, TimeUnit.MILLISECONDS)
            .connectTimeout(100, TimeUnit.MILLISECONDS)
            .addInterceptor(requestInterceptor)
            .addNetworkInterceptor(responseInterceptor)
            .build()

        val retrofit = Retrofit.Builder()
            .client(okHttpClient)
            .baseUrl(mockWebServer.url("/"))
            .addCallAdapterFactory(FlowCallAdapterFactory.create())
            .addConverterFactory(ScalarsConverterFactory.create()) // Use ScalarsConverterFactory
            .build()

        api = retrofit.create(HelloApi::class.java)
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `GIVEN networkOnly fetch strategy WHEN response is enqueued THEN should emit two responses from the original flow`() =
        runTest {
            enqueue("Response 1")

            api.getHello(FetchStrategy.NETWORK_ONLY).test {
                assertEquals("Response 1", awaitItem())
                enqueue("Response 2")
                assertEquals("Response 2", api.getHello(FetchStrategy.NETWORK_ONLY).first())
                assertEquals("Response 2", awaitItem())
                ensureAllEventsConsumed()
            }
        }

    @Test
    fun `GIVEN networkFirst fetch strategy WHEN response is enqueued THEN should emit one response from the original flow`() =
        runTest {
            enqueue("Response 1")

            api.getHello(FetchStrategy.NETWORK_FIRST).test {
                assertEquals("Response 1", awaitItem())
                enqueue("Response 2")
                assertEquals("Response 1", api.getHello(FetchStrategy.CACHE_ONLY).first())
                ensureAllEventsConsumed()
            }
        }

    @Test
    fun `GIVEN networkOnly fetch strategy WHEN 400 response is enqueued THEN should emit error`() =
        runTest {
            enqueue("Error", code = 400)

            api.getHello(FetchStrategy.NETWORK_ONLY).test {
                awaitError()
                ensureAllEventsConsumed()
            }
        }

    @Test
    fun `GIVEN networkOnly fetch strategy WHEN 500 response is enqueued THEN should emit error`() =
        runTest {
            api.getHello(FetchStrategy.NETWORK_ONLY).test {
                awaitError()
                ensureAllEventsConsumed()
            }
        }

    private fun enqueue(value: String?, code: Int = 200) = MockResponse()
        .apply {
            setResponseCode(code)
            value?.let(::setBody)
        }
        .let(mockWebServer::enqueue)
}
