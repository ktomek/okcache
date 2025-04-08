package com.github.ktomek.okcache.exception

import java.io.IOException

class NoCachedResponseException(message: String = "No cached response available") : IOException(message)
