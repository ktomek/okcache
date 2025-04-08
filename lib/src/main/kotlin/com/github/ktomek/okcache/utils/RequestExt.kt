package com.github.ktomek.okcache.utils

import com.github.ktomek.okcache.Cached
import com.github.ktomek.okcache.interceptor.cache.AnnotationCache
import okhttp3.Request
import retrofit2.Invocation

internal fun Request.cacheTag(annotationCache: AnnotationCache): Cached? =
    tag(Invocation::class.java)
        ?.method()
        ?.let { method -> annotationCache.getAnnotation(method, Cached::class.java) }
