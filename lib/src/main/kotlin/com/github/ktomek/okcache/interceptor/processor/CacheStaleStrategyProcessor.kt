package com.github.ktomek.okcache.interceptor.processor

import com.github.ktomek.okcache.GATEWAY_TIMEOUT_CODE
import com.github.ktomek.okcache.exception.NoCachedResponseException
import com.github.ktomek.okcache.utils.proceed
import okhttp3.CacheControl.Companion.FORCE_CACHE
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal object CacheStaleStrategyProcessor : NetworkStrategyProcessor {

    override fun invoke(chain: Interceptor.Chain, request: Request): Response {
        val response = chain.proceed(request, FORCE_CACHE)
        if (response.code == GATEWAY_TIMEOUT_CODE) {
            response.close()
            throw NoCachedResponseException("No cached response available for CACHE_ONLY strategy")
        }
        return response
    }
}
