package com.github.ktomek.okcache.interceptor

import com.github.ktomek.okcache.DefaultNetworkInfoProvider
import com.github.ktomek.okcache.FetchStrategy
import com.github.ktomek.okcache.FetchStrategy.CACHE_FIRST
import com.github.ktomek.okcache.HEADER_FETCH_STRATEGY
import com.github.ktomek.okcache.NetworkInfoProvider
import com.github.ktomek.okcache.interceptor.cache.AnnotationCache
import com.github.ktomek.okcache.interceptor.cache.DefaultAnnotationCache
import com.github.ktomek.okcache.interceptor.processor.DefaultFetchStrategyProcessorMapper
import com.github.ktomek.okcache.interceptor.processor.FetchStrategyProcessorMapper
import com.github.ktomek.okcache.utils.cacheTag
import okhttp3.Interceptor
import okhttp3.Response

internal class InternalRequestCacheControlInterceptor(
    networkInfoProvider: NetworkInfoProvider = DefaultNetworkInfoProvider,
    private val annotationCache: AnnotationCache = DefaultAnnotationCache(),
    private val fetchStrategyProcessorMapper: FetchStrategyProcessorMapper = DefaultFetchStrategyProcessorMapper(
        networkInfoProvider,
        annotationCache
    )
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        // Retrieve the Retrofit Invocation tag to inspect the method annotations.
        request.cacheTag(annotationCache) ?: return chain.proceed(request)

        // Retrieve fetch strategy from custom header (if provided) and remove it from the request.
        return request
            .header(HEADER_FETCH_STRATEGY)
            ?.also { request = request.newBuilder().removeHeader(HEADER_FETCH_STRATEGY).build() }
            ?.let(FetchStrategy::valueOf)
            .orDefault(CACHE_FIRST)
            .let(fetchStrategyProcessorMapper::invoke)
            .invoke(chain, request)
    }

    private fun <T> T?.orDefault(default: T): T = this ?: default
}
