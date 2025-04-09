# OkCache

OkCache is an open-source caching library for Android and JVM applications that leverages OkHttp to provide advanced HTTP caching strategies. It simplifies HTTP caching in your Retrofit APIs by allowing you to enable caching via annotations and choose from several fetch strategies, such as always fetching from the network, caching only, and various fallback approaches.

## Features

- **Annotation-Driven Caching:**  
  Use the `@Cached(maxAge = ...)` annotation on your Retrofit API endpoints to enable caching with a specific expiration time.

- **Multiple Fetch Strategies:**  
  Control caching behavior using fetch strategies:
  - **NETWORK_ONLY:** Always fetch fresh data from the network.
  - **CACHE_ONLY:** Use only the cached response; throw an error if unavailable.
  - **NETWORK_FIRST:** Try the network first; if that fails, fall back to the cache (if the cache is fresh).
  - **CACHE_FIRST:** Try the cache first; if the cache is empty or stale, perform a network call.
  - **CACHE_STALE:** Use the cached response even if stale.

- **Interceptor-Based Implementation:**  
  Custom OkHttp interceptors (both request and network interceptors) manage caching policies based on the provided fetch strategy.

## Installation

If you publish OkCache to Maven Central, add the dependency in your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("com.github.ktomek:okcache:1.0-SNAPSHOT")
}
```

Alternatively, if you’re using [JitPack](https://jitpack.io):

```kotlin
repositories {
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.ktomek:okcache:1.0-SNAPSHOT")
}
```

## Usage

### Annotating Your Retrofit API

Annotate your API methods with `@Cached` to activate caching. For example:

```kotlin
interface MyApiService {
    @Cached(maxAge = 60) // Cache for 60 seconds
    @GET("data")
    fun getData(): Call<MyData>
}
```

### Configuring OkHttp with OkCache

Set up your OkHttpClient with the request and network interceptors:

```kotlin
val networkInfoProvider: NetworkInfoProvider = // your implementation
val cacheDir = File(context.cacheDir, "http_cache")
val cacheSize = 10L * 1024 * 1024  // 10 MB

val okHttpClient = OkHttpClient.Builder()
    .cache(Cache(cacheDir, cacheSize))
    .addInterceptor(RequestCacheControlInterceptor(networkInfoProvider))
    .addNetworkInterceptor(ResponseCacheControlInterceptor())
    .build()
```

### Integrating with Retrofit

Initialize Retrofit with your customized OkHttpClient:

```kotlin
val retrofit = Retrofit.Builder()
    .baseUrl("https://api.example.com/")
    .client(okHttpClient)
    .addConverterFactory(GsonConverterFactory.create())
    .build()

val apiService = retrofit.create(MyApiService::class.java)
```

### Example with FetchStrategy (enum) and Coroutine Support

OkCache allows you to control the caching strategy at runtime using the `FetchStrategy` enum and a dedicated header.

```kotlin
interface MyApiService {
    @Cached(maxAge = 60)
    @GET("data")
    suspend fun getData(@Header(HEADER_FETCH_STRATEGY) strategy: FetchStrategy): MyData
}
```

#### Usage

```kotlin
val apiService = retrofit.create(MyApiService::class.java)

try {
    val result = apiService.getData(FetchStrategy.NETWORK_ONLY)
    // Handle successful result
} catch (e: DeviceOfflineException) {
    // No network available and cache fallback was not allowed
}
```

```kotlin
val apiService = retrofit.create(MyApiService::class.java)

try {
    val result = apiService.getData(FetchStrategy.CACHE_ONLY)
    // Handle successful result
} catch (e: NoCachedResponseException) {
    // Cache was required but not available
}
```



### Exceptions by Strategy

| FetchStrategy      | May Throw Exception                                                             |
|--------------------|----------------------------------------------------------------------------------|
| `CACHE_STALE`      | `NoCachedResponseException` — when no cache exists at all                       |
| `CACHE_ONLY`       | `NoCachedResponseException` — when the cache is missing or expired              |
| `NETWORK_FIRST`    | `DeviceOfflineException` — when offline and cache is empty or not allowed       |
| `NETWORK_ONLY`     | `DeviceOfflineException` — when offline                                         |

## Contributing

Contributions are welcome!  
Please review our [CONTRIBUTING.md](CONTRIBUTING.md) for details on code style, testing, and how to submit pull requests.

## License

This project is licensed under the MIT License.

---

### MIT License

```
MIT License

Copyright (c) 2023 ktomek

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the \"Software\"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED \"AS IS\", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```
