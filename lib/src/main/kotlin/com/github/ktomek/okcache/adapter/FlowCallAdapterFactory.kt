package com.github.ktomek.okcache.adapter

import kotlinx.coroutines.flow.Flow
import retrofit2.CallAdapter
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * [CallAdapter.Factory] that allows to use [Flow] as a return type in Retrofit interface.
 * It caches flows in [InMemoryFlowsCache].
 */
class FlowCallAdapterFactory : CallAdapter.Factory() {

    private val cache = InMemoryFlowsCache()

    override fun get(
        returnType: Type,
        annotations: Array<out Annotation?>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? = returnType
        .takeIf { getRawType(it) == Flow::class.java }
        ?.let { it as? ParameterizedType }
        ?.let { getParameterUpperBound(0, it) }
        ?.let { FlowAdapter<Any>(it, cache) }

    companion object {
        @JvmStatic
        fun create(): FlowCallAdapterFactory = FlowCallAdapterFactory()
    }
}
