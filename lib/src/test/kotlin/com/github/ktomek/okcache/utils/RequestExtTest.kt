package com.github.ktomek.okcache.utils

import com.github.ktomek.okcache.Cached
import com.github.ktomek.okcache.interceptor.cache.AnnotationCache
import io.mockk.every
import io.mockk.mockk
import okhttp3.Request
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import retrofit2.Invocation
import java.lang.reflect.Method

internal class RequestExtTest {

    // Dummy interface with a method annotated with @Cached.
    interface DummyWithCached {
        @Cached(maxAge = 60)
        fun foo()
    }

    // Dummy interface with a method without any @Cached annotation.
    interface DummyWithoutCached {
        fun bar()
    }

    // Use a mocked AnnotationCache.
    private val annotationCache: AnnotationCache = mockk()

    @Test
    fun `GIVEN request without Invocation tag WHEN calling cacheTag THEN returns null`() {
        // GIVEN: a Request with no Invocation tag.
        val request = Request.Builder().url("http://example.com").build()
        // WHEN: calling cacheTag.
        val result = request.cacheTag(annotationCache)
        // THEN: result is null.
        assertNull(result)
    }

    @Test
    fun `GIVEN Invocation method not annotated with Cached WHEN calling cacheTag THEN returns null`() {
        // GIVEN: an Invocation for a method without @Cached.
        val method: Method = DummyWithoutCached::class.java.getMethod("bar")
        val invocation = mockk<Invocation> {
            every { method() } returns method
        }
        // Configure the annotationCache to return null.
        every { annotationCache.getAnnotation(method, Cached::class.java) } returns null

        val request = Request.Builder()
            .url("http://example.com")
            .tag(Invocation::class.java, invocation)
            .build()
        // WHEN: calling cacheTag.
        val result = request.cacheTag(annotationCache)
        // THEN: result is null.
        assertNull(result)
    }

    @Test
    fun `GIVEN Invocation method annotated with Cached WHEN calling cacheTag THEN returns Cached annotation`() {
        // GIVEN: an Invocation for a method annotated with @Cached.
        val method: Method = DummyWithCached::class.java.getMethod("foo")
        val invocation = mockk<Invocation> {
            every { method() } returns method
        }
        // Configure the annotationCache to return the actual annotation.
        val expectedAnnotation = method.getAnnotation(Cached::class.java)
        every { annotationCache.getAnnotation(method, Cached::class.java) } returns expectedAnnotation

        val request = Request.Builder()
            .url("http://example.com")
            .tag(Invocation::class.java, invocation)
            .build()
        // WHEN: calling cacheTag.
        val result = request.cacheTag(annotationCache)
        // THEN: result is not null and has the expected maxAge.
        assertNotNull(result)
        assertEquals(60, result?.maxAge)
    }
}
