package com.util.ktor.serializer

import kotlinx.serialization.json.Json
import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class BigDecimalSerializerTest {

    @Test
    fun `serialize regular BigDecimal`() {
        val json = Json
        val result = json.encodeToString(BigDecimalSerializer, BigDecimal("123.456"))
        assertEquals("\"123.456\"", result)
    }

    @Test
    fun `serialize BigDecimal without scientific notation`() {
        val json = Json
        val result =
            json.encodeToString(BigDecimalSerializer, BigDecimal("1.0E-10"))
        assertEquals("\"0.0000000001\"", result)
    }

    @Test
    fun `serialize zero BigDecimal`() {
        val json = Json
        val result = json.encodeToString(BigDecimalSerializer, BigDecimal.ZERO)
        assertEquals("\"0\"", result)
    }

    @Test
    fun `serialize large BigDecimal`() {
        val json = Json
        val result = json.encodeToString(
            BigDecimalSerializer,
            BigDecimal("999999999999999999.9999999999")
        )
        assertEquals("\"999999999999999999.9999999999\"", result)
    }

    @Test
    fun `deserialize string to BigDecimal`() {
        val json = Json
        val result = json.decodeFromString(BigDecimalSerializer, "\"123.456\"")
        assertEquals(BigDecimal("123.456"), result)
    }

    @Test
    fun `deserialize integer string to BigDecimal`() {
        val json = Json
        val result = json.decodeFromString(BigDecimalSerializer, "\"100\"")
        assertEquals(BigDecimal("100"), result)
    }

    @Test
    fun `round trip serialization`() {
        val json = Json
        val original = BigDecimal("3.14159265358979323846")
        val serialized = json.encodeToString(BigDecimalSerializer, original)
        val deserialized = json.decodeFromString(BigDecimalSerializer, serialized)
        assertEquals(original, deserialized)
    }
}
