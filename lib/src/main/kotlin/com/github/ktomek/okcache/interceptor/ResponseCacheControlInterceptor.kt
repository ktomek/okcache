package com.github.ktomek.okcache.interceptor

import com.github.ktomek.okcache.Cached
import com.github.ktomek.okcache.interceptor.cache.AnnotationCache
import com.github.ktomek.okcache.interceptor.cache.DefaultAnnotationCache
import com.github.ktomek.okcache.utils.applyCache
import com.github.ktomek.okcache.utils.cacheControl
import com.github.ktomek.okcache.utils.cacheTag
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Interceptor that applies cache control headers to successful responses.
 * It uses [AnnotationCache] to retrieve cache control information from request tags.
 */
class ResponseCacheControlInterceptor : Interceptor {

    private val annotationCache: AnnotationCache = DefaultAnnotationCache()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val cachedAnnotation = request.cacheTag(annotationCache) ?: return chain.proceed(request)

        val response = chain.proceed(request)
        return when (response.isSuccessful) {
            true -> response.responseWithCacheControl(cachedAnnotation)
            false -> response
        }
    }

    private fun Response.responseWithCacheControl(cachedAnnotation: Cached): Response = newBuilder()
        .applyCache(cachedAnnotation.cacheControl())
        .build()
}
