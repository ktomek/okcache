package com.github.ktomek.okcache.interceptor.processor

import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

internal interface NetworkStrategyProcessor {
    operator fun invoke(chain: Interceptor.Chain, request: Request): Response
}
