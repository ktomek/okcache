package com.github.ktomek.okcache.interceptor

import com.github.ktomek.okcache.DefaultNetworkInfoProvider
import com.github.ktomek.okcache.NetworkInfoProvider
import okhttp3.Interceptor

/**
 * An interceptor that adds cache control headers to requests.
 *
 *
 * @param networkInfoProvider a provider of network connection information.
 * @see InternalRequestCacheControlInterceptor
 */
class RequestCacheControlInterceptor(
    networkInfoProvider: NetworkInfoProvider = DefaultNetworkInfoProvider
) : Interceptor by InternalRequestCacheControlInterceptor(networkInfoProvider)
