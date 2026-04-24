package com.util.ktor.serializer

import kotlinx.serialization.json.Json
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BigDecimalSerializerBoundaryTest {

    private val json = Json

    //region Positive boundary values

    @Test
    fun `serialize BigDecimal ONE`() {
        val result = json.encodeToString(BigDecimalSerializer, BigDecimal.ONE)
        assertEquals("\"1\"", result)
    }

    @Test
    fun `serialize BigDecimal TEN`() {
        val result = json.encodeToString(BigDecimalSerializer, BigDecimal.TEN)
        assertEquals("\"10\"", result)
    }

    @Test
    fun `serialize very large BigDecimal`() {
        val value = BigDecimal("999999999999999999999999999999999.9999999999")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"999999999999999999999999999999999.9999999999\"", result)
    }

    @Test
    fun `serialize very small positive BigDecimal`() {
        val value = BigDecimal("0.000000000000000000000000001")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"0.000000000000000000000000001\"", result)
    }

    @Test
    fun `serialize BigDecimal with many decimal places`() {
        val value = BigDecimal("3.14159265358979323846264338327950288419716939937510")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"3.14159265358979323846264338327950288419716939937510\"", result)
    }

    //endregion

    //region Negative boundary values

    @Test
    fun `serialize negative BigDecimal`() {
        val value = BigDecimal("-123.456")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"-123.456\"", result)
    }

    @Test
    fun `serialize very large negative BigDecimal`() {
        val value = BigDecimal("-999999999999999999.9999999999")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"-999999999999999999.9999999999\"", result)
    }

    @Test
    fun `serialize negative zero`() {
        val value = BigDecimal("-0")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"0\"", result)
    }

    //endregion

    //region Zero variations

    @Test
    fun `serialize BigDecimal zero with trailing zeros`() {
        val value = BigDecimal("0.00")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"0.00\"", result)
    }

    @Test
    fun `serialize BigDecimal with integer trailing zeros`() {
        val value = BigDecimal("100.00")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"100.00\"", result)
    }

    //endregion

    //region Scientific notation handling

    @Test
    fun `serialize scientific notation small value to plain string`() {
        val value = BigDecimal("1E-20")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"0.00000000000000000001\"", result)
    }

    @Test
    fun `serialize scientific notation large value to plain string`() {
        val value = BigDecimal("1E+20")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"100000000000000000000\"", result)
    }

    @Test
    fun `serialize negative scientific notation`() {
        val value = BigDecimal("-5.0E-10")
        val result = json.encodeToString(BigDecimalSerializer, value)
        assertEquals("\"-0.00000000050\"", result)
    }

    //endregion

    //region Deserialization edge cases

    @Test
    fun `deserialize negative value`() {
        val result = json.decodeFromString(BigDecimalSerializer, "\"-999.99\"")
        assertEquals(BigDecimal("-999.99"), result)
    }

    @Test
    fun `deserialize zero string`() {
        val result = json.decodeFromString(BigDecimalSerializer, "\"0\"")
        assertEquals(BigDecimal.ZERO, result)
    }

    @Test
    fun `deserialize zero with decimals`() {
        val result = json.decodeFromString(BigDecimalSerializer, "\"0.000\"")
        assertEquals(BigDecimal("0.000"), result)
    }

    @Test
    fun `deserialize very long decimal string`() {
        val decimalStr = "123456789.123456789012345678901234567890"
        val result = json.decodeFromString(BigDecimalSerializer, "\"$decimalStr\"")
        assertEquals(BigDecimal(decimalStr), result)
    }

    @Test
    fun `deserialize integer value`() {
        val result = json.decodeFromString(BigDecimalSerializer, "\"42\"")
        assertEquals(BigDecimal("42"), result)
    }

    //endregion

    //region Round trip for all boundaries

    @Test
    fun `round trip for zero`() {
        val original = BigDecimal.ZERO
        val serialized = json.encodeToString(BigDecimalSerializer, original)
        val deserialized = json.decodeFromString(BigDecimalSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `round trip for negative value`() {
        val original = BigDecimal("-987654321.123456789")
        val serialized = json.encodeToString(BigDecimalSerializer, original)
        val deserialized = json.decodeFromString(BigDecimalSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `round trip for very small scientific notation`() {
        val original = BigDecimal("1.0E-50")
        val serialized = json.encodeToString(BigDecimalSerializer, original)
        val deserialized = json.decodeFromString(BigDecimalSerializer, serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `round trip for very large value`() {
        val original = BigDecimal("1.0E+50")
        val serialized = json.encodeToString(BigDecimalSerializer, original)
        val deserialized = json.decodeFromString(BigDecimalSerializer, serialized)
        assertTrue(
            original.compareTo(deserialized) == 0,
            "Expected $original but got $deserialized"
        )
    }

    @Test
    fun `round trip preserves scale`() {
        val original = BigDecimal("1.00")
        val serialized = json.encodeToString(BigDecimalSerializer, original)
        val deserialized = json.decodeFromString(BigDecimalSerializer, serialized)
        assertEquals(original, deserialized)
        assertEquals(original.scale(), deserialized.scale())
    }

    //endregion

    //region Descriptor verification

    @Test
    fun `descriptor name is BigDecimal`() {
        assertEquals("BigDecimal", BigDecimalSerializer.descriptor.serialName)
    }

    @Test
    fun `descriptor kind is STRING`() {
        assertEquals(
            kotlinx.serialization.descriptors.PrimitiveKind.STRING,
            BigDecimalSerializer.descriptor.kind
        )
    }

    //endregion
}
