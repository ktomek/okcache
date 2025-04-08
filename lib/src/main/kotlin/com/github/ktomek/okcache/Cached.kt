package com.github.ktomek.okcache

/**
 * Marks a function as cacheable.
 *
 * This annotation indicates that the result of the annotated function should be cached
 * for a specified duration. The cache is managed by the OkCache library, which intercepts
 * network requests and responses to handle caching logic.
 *
 * @property maxAge The maximum age, in seconds, that the cached response is considered valid.
 *                   After this time, the cache will be invalidated, and a new network request
 *                   will be made to fetch fresh data.
 *
 *                   A value of 0 means that the response should not be cached.
 *                   A negative value means that the response should be cached indefinitely.
 *
 * @constructor Creates a new Cached annotation with the specified maximum age.
 *
 * Example Usage:
 *
 * ```
 *      interface MyApi {
 *          @Cached(maxAge = 300) // Cache the response for 5 minutes (300 seconds)
 *          @GET("/data")
 *          suspend fun getData(): Data
 *
 *          @Cached(maxAge = 0) // Do not cache the response
 *          @GET("/no-cache")
 *          suspend fun getNoCache(): Data
 *
 *          @Cached(maxAge = -1) // Cache the response indefinitely
 *          @GET("/indefinite-cache")
 *          suspend fun getIndefiniteCache(): Data
 *     }
 *```
 *
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Cached(val maxAge: Int) // maxAge in seconds for cache expiration.
