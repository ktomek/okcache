package com.github.ktomek.okcache.interceptor.processor

import com.github.ktomek.okcache.GATEWAY_TIMEOUT_CODE
import com.github.ktomek.okcache.exception.NoCachedResponseException
import com.github.ktomek.okcache.interceptor.cache.AnnotationCache
import com.github.ktomek.okcache.interceptor.cache.DefaultAnnotationCache
import com.github.ktomek.okcache.utils.cacheTag
import com.github.ktomek.okcache.utils.proceed
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.concurrent.TimeUnit.SECONDS

internal class CacheOnlyStrategyProcessor(
    private val annotationCache: AnnotationCache = DefaultAnnotationCache()
) : NetworkStrategyProcessor {

    override fun invoke(chain: Interceptor.Chain, request: Request): Response {
        val cachedAnnotation = checkNotNull(request.cacheTag(annotationCache)) {
            "Method should be annotated with @Cached"
        }

        val response = chain.proceed(
            request = request,
            cacheControl = CacheControl.Builder().onlyIfCached().maxAge(cachedAnnotation.maxAge, SECONDS).build()
        )
        if (response.code == GATEWAY_TIMEOUT_CODE) {
            response.close()
            throw NoCachedResponseException("No cached response available for CACHE_ONLY strategy")
        }
        return response
    }
}
