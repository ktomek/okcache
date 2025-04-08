package com.github.ktomek.okcache.interceptor.processor

import com.github.ktomek.okcache.FetchStrategy
import com.github.ktomek.okcache.FetchStrategy.CACHE_FIRST
import com.github.ktomek.okcache.FetchStrategy.CACHE_ONLY
import com.github.ktomek.okcache.FetchStrategy.CACHE_STALE
import com.github.ktomek.okcache.FetchStrategy.NETWORK_FIRST
import com.github.ktomek.okcache.FetchStrategy.NETWORK_ONLY
import com.github.ktomek.okcache.NetworkInfoProvider
import com.github.ktomek.okcache.interceptor.cache.AnnotationCache

internal interface FetchStrategyProcessorMapper {
    operator fun invoke(fetchStrategy: FetchStrategy): NetworkStrategyProcessor
}

internal class DefaultFetchStrategyProcessorMapper(
    networkInfoProvider: NetworkInfoProvider,
    annotationCache: AnnotationCache,
) : FetchStrategyProcessorMapper {
    private val networkFirstInterceptor: NetworkStrategyProcessor = NetworkFirstStrategyProcessor(networkInfoProvider)
    private val cacheFirstInterceptor: NetworkStrategyProcessor = CacheFirstStrategyProcessor(annotationCache)
    private val cacheOnlyInterceptor: NetworkStrategyProcessor = CacheOnlyStrategyProcessor(annotationCache)
    private val networkOnlyInterceptor: NetworkStrategyProcessor = NetworkOnlyStrategyProcessor(networkInfoProvider)
    private val cacheStaleInterceptor: NetworkStrategyProcessor = CacheStaleStrategyProcessor

    override fun invoke(fetchStrategy: FetchStrategy): NetworkStrategyProcessor =
        fetchStrategy.getProcessor()

    private fun FetchStrategy.getProcessor(): NetworkStrategyProcessor = when (this) {
        NETWORK_ONLY -> networkOnlyInterceptor
        CACHE_ONLY -> cacheOnlyInterceptor
        NETWORK_FIRST -> networkFirstInterceptor
        CACHE_FIRST -> cacheFirstInterceptor
        CACHE_STALE -> cacheStaleInterceptor
    }
}
