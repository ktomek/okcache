package com.github.ktomek.okcache.interceptor.processor

import com.github.ktomek.okcache.NetworkInfoProvider
import com.github.ktomek.okcache.exception.DeviceOfflineException
import com.github.ktomek.okcache.utils.proceed
import okhttp3.CacheControl.Companion.FORCE_NETWORK
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal class NetworkOnlyStrategyProcessor(
    private val networkInfoProvider: NetworkInfoProvider
) : NetworkStrategyProcessor {

    override fun invoke(chain: Interceptor.Chain, request: Request): Response {
        if (!networkInfoProvider.isNetworkAvailable()) {
            throw DeviceOfflineException("No network available for NETWORK_ONLY strategy")
        }
        return chain.proceed(request, FORCE_NETWORK)
    }
}
