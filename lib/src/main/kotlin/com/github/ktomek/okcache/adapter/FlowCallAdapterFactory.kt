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
    ): CallAdapter<*, *>? {
        if (returnType !is ParameterizedType || getRawType(returnType) != Flow::class.java) return null
        val flowType = getParameterUpperBound(0, returnType)
        return FlowAdapter<Any>(flowType, cache)
    }

    companion object {
        @JvmStatic
        fun create(): FlowCallAdapterFactory = FlowCallAdapterFactory()
    }
}
