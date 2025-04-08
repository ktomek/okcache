package com.github.ktomek.okcache

/**
 * Provides information about network availability.
 */
interface NetworkInfoProvider {
    /**
     * Checks if the network is available.
     *
     * @return `true` if the network is available, `false` otherwise.
     */
    fun isNetworkAvailable(): Boolean
}

/**
 * Default implementation of [NetworkInfoProvider] that always returns `true`.
 */
internal object DefaultNetworkInfoProvider : NetworkInfoProvider {
    override fun isNetworkAvailable(): Boolean = true
}
