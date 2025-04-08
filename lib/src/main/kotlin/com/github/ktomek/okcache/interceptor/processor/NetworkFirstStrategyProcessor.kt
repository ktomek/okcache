package com.github.ktomek.okcache.interceptor.processor

import com.github.ktomek.okcache.GATEWAY_TIMEOUT_CODE
import com.github.ktomek.okcache.NetworkInfoProvider
import com.github.ktomek.okcache.exception.DeviceOfflineException
import com.github.ktomek.okcache.utils.proceed
import okhttp3.CacheControl
import okhttp3.CacheControl.Companion.FORCE_NETWORK
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

internal class NetworkFirstStrategyProcessor(private val networkInfoProvider: NetworkInfoProvider) :
    NetworkStrategyProcessor {

    override fun invoke(chain: Interceptor.Chain, request: Request): Response =
        if (networkInfoProvider.isNetworkAvailable()) {
            try {
                // Force a network call with no caching.
                chain.proceed(request, FORCE_NETWORK)
            } catch (e: IOException) {
                println("Network call failed, fallback to cache: ${e.message}")
                // Fallback: use cache-only header if network call fails.
                chain.proceed(request, CacheControl.Builder().onlyIfCached().build())
                    .takeIf { it.code != GATEWAY_TIMEOUT_CODE }
                    ?: throw e
            }
        } else {
            chain.proceed(request, CacheControl.Builder().onlyIfCached().build())
                .takeIf { it.code != GATEWAY_TIMEOUT_CODE }
                ?: throw DeviceOfflineException()
        }
}
