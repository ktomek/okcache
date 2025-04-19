package com.github.ktomek.okcache.adapter

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.Request

/**
 * Cache of [Flow]s associated with [Request]s.
 * It allows to share the same [Flow] between multiple subscribers.
 */
internal interface FlowsCache {
    /**
     * Returns a [Flow] associated with the given [request].
     *
     * If there is no [Flow] associated with the given [request], a new one is created.
     *
     * @param request the request to get the [Flow] for
     * @return a [Flow] associated with the given [request]
     */
    fun <T : Any> getFlow(request: Request): Flow<T>

    /**
     * Emits a [value] to the [Flow] associated with the given [request].
     * @param request the request to emit the [value] to
     * @param value the value to emit
     */
    suspend fun <T : Any> emit(request: Request, value: T)
}

internal class InMemoryFlowsCache : FlowsCache {
    private val cacheMap = mutableMapOf<String, MutableSharedFlow<Any>>()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getFlow(request: Request): Flow<T> {
        val key = requestKey(request)
        return (cacheMap.getOrPut(key) { MutableSharedFlow() } as MutableSharedFlow<T>)
    }

    override suspend fun <T : Any> emit(request: Request, value: T) {
        val key = requestKey(request)
        val flow = cacheMap.getOrPut(key) { MutableSharedFlow<Any>() }
        flow.emit(value)
    }

    private fun requestKey(request: Request): String {
        val url = request.url.toString()
        val headers = request.headers.joinToString(separator = "&") { "${it.first}=${it.second}" }
        return "$url|$headers"
    }
}
