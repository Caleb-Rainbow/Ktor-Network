package com.util.ktor.data.login.model

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserTokenSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `deserialize UserToken with all fields`() {
        val jsonStr = """{
            "token":"abc123",
            "officeId":42,
            "tenant":"my-tenant",
            "forceChangePassword":true,
            "forceChangeReason":"密码已过期"
        }"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals("abc123", token.token)
        assertEquals(42, token.officeId)
        assertEquals("my-tenant", token.tenant)
        assertEquals(true, token.forceChangePassword)
        assertEquals("密码已过期", token.forceChangeReason)
    }

    @Test
    fun `deserialize UserToken with deptId alias maps to officeId`() {
        val jsonStr = """{"token":"t","deptId":99}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals(99, token.officeId)
    }

    @Test
    fun `deserialize UserToken with only token`() {
        val jsonStr = """{"token":"minimal"}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals("minimal", token.token)
        assertNull(token.officeId)
        assertNull(token.tenant)
        assertNull(token.forceChangePassword)
        assertNull(token.forceChangeReason)
    }

    @Test
    fun `deserialize UserToken with empty token`() {
        val jsonStr = """{"token":""}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals("", token.token)
    }

    @Test
    fun `deserialize UserToken with zero officeId`() {
        val jsonStr = """{"token":"t","officeId":0}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals(0, token.officeId)
    }

    @Test
    fun `deserialize UserToken with negative officeId`() {
        val jsonStr = """{"token":"t","officeId":-1}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals(-1, token.officeId)
    }

    @Test
    fun `deserialize UserToken with large officeId`() {
        val jsonStr = """{"token":"t","officeId":2147483647}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals(Int.MAX_VALUE, token.officeId)
    }

    @Test
    fun `deserialize UserToken with empty tenant`() {
        val jsonStr = """{"token":"t","tenant":""}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals("", token.tenant)
    }

    @Test
    fun `deserialize UserToken with forceChangePassword false`() {
        val jsonStr = """{"token":"t","forceChangePassword":false}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals(false, token.forceChangePassword)
    }

    @Test
    fun `deserialize UserToken with empty forceChangeReason`() {
        val jsonStr = """{"token":"t","forceChangeReason":""}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals("", token.forceChangeReason)
    }

    @Test
    fun `deserialize UserToken ignores unknown fields`() {
        val jsonStr = """{"token":"t","unknownField":"value","extra":123}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals("t", token.token)
    }

    @Test
    fun `UserToken round trip serialization`() {
        val original = UserToken(
            token = "round-trip-token",
            officeId = 42,
            tenant = "tenant-42",
            forceChangePassword = true,
            forceChangeReason = "安全策略"
        )
        val serialized = json.encodeToString(UserToken.serializer(), original)
        val deserialized = json.decodeFromString(UserToken.serializer(), serialized)
        assertEquals(original, deserialized)
    }

    @Test
    fun `UserToken with very long token string`() {
        val longToken = "a".repeat(10000)
        val jsonStr = """{"token":"$longToken"}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals(longToken, token.token)
    }

    @Test
    fun `UserToken with special characters in token`() {
        val specialToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.dozjgNryP4J3jVmNHl0w5N_XgL0n3I9PlFUP0THsR8U"
        val jsonStr = """{"token":"$specialToken"}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals(specialToken, token.token)
    }
}
