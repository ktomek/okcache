package com.github.ktomek.okcache.utils

import com.github.ktomek.okcache.HEADER_CACHE_CONTROL
import okhttp3.CacheControl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal fun Response.Builder.applyCache(cacheControl: CacheControl) =
    this.header(HEADER_CACHE_CONTROL, cacheControl.toString())

internal fun Request.Builder.applyCache(cacheControl: CacheControl) =
    this.header(HEADER_CACHE_CONTROL, cacheControl.toString())

internal fun Interceptor.Chain.proceed(request: Request, cacheControl: CacheControl) =
    request
        .newBuilder()
        .applyCache(cacheControl)
        .build()
        .let { proceed(it) }
