package com.github.ktomek.okcache.adapter

import app.cash.turbine.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Response

@Suppress("UNCHECKED_CAST")
class FlowAdapterTest {

    private val mockFlowsCache: FlowsCache = mockk(relaxed = true)
    private val adapter = FlowAdapter<String>(String::class.java, mockFlowsCache)

    @Test
    fun `GIVEN a successful call WHEN adapt is called THEN should emit the correct value`() = runTest {
        val mockCall: Call<String> = mockk()
        val expectedResponse = "Hello, World!"
        val request = mockk<Request>()

        val response: Response<String> = Response.success(expectedResponse)
        coEvery { mockCall.enqueue(any()) } answers {
            val callback = it.invocation.args.first() as retrofit2.Callback<String>
            callback.onResponse(mockCall, response)
        }
        every { mockCall.request() } returns request

        adapter.adapt(mockCall).test {
            assertEquals(expectedResponse, awaitItem())
            coVerify { mockFlowsCache.emit<String>(request, expectedResponse) }
            awaitComplete()
        }
    }

    @Test
    fun `GIVEN a failed call WHEN adapt is called THEN should propagate the exception`() = runTest {
        val mockCall: Call<String> = mockk()
        val expectedError = HttpException(Response.error<String>(404, "Not Found".toResponseBody()))

        coEvery { mockCall.enqueue(any()) } answers {
            val callback = it.invocation.args.first() as retrofit2.Callback<String>
            callback.onFailure(mockCall, expectedError)
        }

        adapter.adapt(mockCall).test {
            awaitError()
        }
    }

    @Test
    fun `GIVEN response body throws exception WHEN adapt is called THEN should propagate the exception`() = runTest {
        val mockCall: Call<String> = mockk()

        coEvery { mockCall.enqueue(any()) } answers {
            val callback = it.invocation.args.first() as retrofit2.Callback<String>
            callback.onResponse(mockCall, Response.success(null)) // Null body to simulate failure
        }

        // Act
        adapter.adapt(mockCall).test {
            awaitError()
        }
    }
}
