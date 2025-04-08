package com.github.ktomek.okcache

/**
 * Defines the strategy for fetching data, determining the order of preference between
 * network requests and cached responses.
 *
 * This enum provides several options for how the OkCache library should handle data
 * retrieval, allowing you to fine-tune the balance between freshness and performance
 * based on your application's needs.
 *
 * @see com.github.ktomek.okcache.Cached
 * @see com.github.ktomek.okcache.interceptor.processor.NetworkFirstStrategyProcessor
 * @see com.github.ktomek.okcache.interceptor.processor.CacheFirstStrategyProcessor
 */
enum class FetchStrategy {
    /**
     * Always fetches fresh data from the network.
     *
     * This strategy bypasses the cache entirely and always makes a network request.
     * If the network request fails, an error will be thrown.
     */
    NETWORK_ONLY,

    /**
     * Only uses cached responses.
     *
     * This strategy will only use data from the cache. If the data is not available
     * in the cache, an error will be thrown.
     */
    CACHE_ONLY,

    /**
     * Tries the network first, falling back to the cache if the network is unavailable or fails.
     *
     * This strategy attempts to fetch data from the network. If the network request
     * succeeds, the response is used. If the network request fails (e.g., due to
     * network connectivity issues), the cached response is used as a fallback.
     * If the cache is empty and the network fails, an error will be thrown.
     */
    NETWORK_FIRST,

    /**
     * Tries the cache first, falling back to the network if the cache is empty or expired.
     *
     * This strategy first checks the cache for a valid response. If a valid cached
     * response is found, it is used. If the cache is empty or the cached response is
     * expired, a network request is made to fetch fresh data.
     */
    CACHE_FIRST,

    /**
     * Returns the cached response if available, regardless of its age.
     *
     * This strategy prioritizes speed by immediately returning the cached response, even if it's stale.
     * If the cache is empty, a [com.github.ktomek.okcache.exception.NoCachedResponseException] will be thrown.
     * If the cache is not empty, the cached response will be returned.
     */
    CACHE_STALE,
}
