package com.github.ktomek.okcache.interceptor.cache

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

internal interface AnnotationCache {
    fun <T : Annotation> getAnnotation(method: Method, annotationClass: Class<T>): T?
}

internal class DefaultAnnotationCache : AnnotationCache {
    private val cache = ConcurrentHashMap<Pair<Method, Class<out Annotation>>, Annotation?>()

    override fun <T : Annotation> getAnnotation(method: Method, annotationClass: Class<T>): T? {
        val key = Pair(method, annotationClass)
        @Suppress("UNCHECKED_CAST")
        return cache.computeIfAbsent(key) { method.getAnnotation(annotationClass) } as T?
    }
}
