package com.github.ktomek.okcache.adapter

import kotlinx.coroutines.flow.Flow
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

class FlowCallAdapterFactoryTest {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://example.com")
        .build()

    private val factory = FlowCallAdapterFactory.create()

    @Test
    fun `GIVEN returnType is not Flow WHEN get is called THEN should return null`() {
        val nonFlowType: Type = String::class.java

        val adapter = factory.get(nonFlowType, emptyArray(), retrofit)

        assertNull(adapter)
    }

    @Test
    fun `GIVEN returnType is Flow of String WHEN get is called THEN should return FlowAdapter`() {
        val parameterizedType = object : ParameterizedType {
            override fun getRawType(): Type = Flow::class.java
            override fun getActualTypeArguments(): Array<Type> = arrayOf(String::class.java)
            override fun getOwnerType(): Type? = null
        }

        val adapter = factory.get(parameterizedType, emptyArray(), retrofit)

        assertNotNull(adapter)
        assert(adapter is FlowAdapter<*>)
    }

    @Test
    fun `GIVEN returnType is Flow with no parameterized type WHEN get is called THEN should return null`() {
        val parameterizedType = object : ParameterizedType {
            override fun getRawType(): Type = String::class.java
            override fun getActualTypeArguments(): Array<Type> = emptyArray()
            override fun getOwnerType(): Type? = null
        }

        val adapter = factory.get(parameterizedType, emptyArray(), retrofit)

        assertNull(adapter)
    }
}
