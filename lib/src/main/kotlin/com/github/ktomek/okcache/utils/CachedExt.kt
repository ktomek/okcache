package com.github.ktomek.okcache.utils

import com.github.ktomek.okcache.Cached
import okhttp3.CacheControl
import java.util.concurrent.TimeUnit

internal fun Cached.cacheControl(): CacheControl = CacheControl
    .Builder()
    .maxAge(this.maxAge, TimeUnit.SECONDS)
    .build()
