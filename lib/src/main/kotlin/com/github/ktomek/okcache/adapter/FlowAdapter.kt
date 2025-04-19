package com.github.ktomek.okcache.adapter

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.suspendCancellableCoroutine
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Callback
import retrofit2.Response
import java.lang.reflect.Type
import kotlin.coroutines.resumeWithException

internal class FlowAdapter<T : Any>(
    private val type: Type,
    private val flowsCache: FlowsCache
) : CallAdapter<T, Flow<T>> {
    override fun responseType(): Type = type

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun adapt(call: Call<T>): Flow<T> = flow {
        val value = suspendCancellableCoroutine<T> { continuation ->
            call.enqueue(object : Callback<T> {

                @Suppress("TooGenericExceptionCaught")
                override fun onResponse(call: Call<T>, response: Response<T>) = try {
                    continuation.resume(response.body()!!) { cause, _, _ ->
                        continuation.resumeWithException(cause)
                    }
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }

                override fun onFailure(call: Call<T>, t: Throwable) {
                    continuation.resumeWithException(t)
                }
            })
        }
        emit(value)
    }
        .onEach { flowsCache.emit<T>(call.request(), it) } // flowsCache.emit<T>(call.request(), it) }
        .flatMapLatest { flowsCache.getFlow<T>(call.request()).onStart { emit(it) } }
        .distinctUntilChanged()
}
