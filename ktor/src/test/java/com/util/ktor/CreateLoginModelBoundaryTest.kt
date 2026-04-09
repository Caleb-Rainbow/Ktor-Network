package com.util.ktor

import com.util.ktor.config.LoginKeyStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CreateLoginModelBoundaryTest {

    //region Basic functionality

    @Test
    fun `CAMEL_CASE_V1 produces correct key names`() {
        val result = createLoginModel(Json, LoginKeyStyle.CAMEL_CASE_V1, "user", "pass")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertTrue(obj.containsKey("userName"))
        assertTrue(obj.containsKey("passWord"))
        assertFalse(obj.containsKey("username"))
        assertFalse(obj.containsKey("password"))
    }

    @Test
    fun `LOWER_CASE_V2 produces correct key names`() {
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, "user", "pass")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertTrue(obj.containsKey("username"))
        assertTrue(obj.containsKey("password"))
        assertFalse(obj.containsKey("userName"))
        assertFalse(obj.containsKey("passWord"))
    }

    //endregion

    //region Empty inputs

    @Test
    fun `empty username and password`() {
        for (style in LoginKeyStyle.entries) {
            val result = createLoginModel(Json, style, "", "")
            val obj = Json.parseToJsonElement(result).jsonObject
            val userKey = if (style == LoginKeyStyle.CAMEL_CASE_V1) "userName" else "username"
            val passKey = if (style == LoginKeyStyle.CAMEL_CASE_V1) "passWord" else "password"
            assertEquals("", obj[userKey]?.jsonPrimitive?.content)
            assertEquals("", obj[passKey]?.jsonPrimitive?.content)
        }
    }

    @Test
    fun `empty username only`() {
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, "", "pass")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals("", obj["username"]?.jsonPrimitive?.content)
        assertEquals("pass", obj["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun `empty password only`() {
        val result = createLoginModel(Json, LoginKeyStyle.CAMEL_CASE_V1, "user", "")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals("user", obj["userName"]?.jsonPrimitive?.content)
        assertEquals("", obj["passWord"]?.jsonPrimitive?.content)
    }

    //endregion

    //region Special characters

    @Test
    fun `username with email format`() {
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, "user@domain.com", "pass")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals("user@domain.com", obj["username"]?.jsonPrimitive?.content)
    }

    @Test
    fun `password with special characters`() {
        val password = "p@ss!w0rd#$%^&*()_+-=[]{}|;':\",./<>?"
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, "user", password)
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(password, obj["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun `username with unicode characters`() {
        val username = "用户名"
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, username, "pass")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(username, obj["username"]?.jsonPrimitive?.content)
    }

    @Test
    fun `password with unicode characters`() {
        val password = "密码123"
        val result = createLoginModel(Json, LoginKeyStyle.CAMEL_CASE_V1, "user", password)
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(password, obj["passWord"]?.jsonPrimitive?.content)
    }

    @Test
    fun `username with emoji`() {
        val username = "user\uD83D\uDE00name"
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, username, "pass")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(username, obj["username"]?.jsonPrimitive?.content)
    }

    @Test
    fun `password with newline characters`() {
        val password = "pass\nword"
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, "user", password)
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(password, obj["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun `username with tab characters`() {
        val username = "user\tname"
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, username, "pass")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(username, obj["username"]?.jsonPrimitive?.content)
    }

    //endregion

    //region Long inputs

    @Test
    fun `very long username`() {
        val username = "a".repeat(10000)
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, username, "pass")
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(username, obj["username"]?.jsonPrimitive?.content)
    }

    @Test
    fun `very long password`() {
        val password = "x".repeat(10000)
        val result = createLoginModel(Json, LoginKeyStyle.CAMEL_CASE_V1, "user", password)
        val obj = Json.parseToJsonElement(result).jsonObject
        assertEquals(password, obj["passWord"]?.jsonPrimitive?.content)
    }

    //endregion

    //region JSON validity

    @Test
    fun `output is valid JSON for CAMEL_CASE_V1`() {
        val result = createLoginModel(Json, LoginKeyStyle.CAMEL_CASE_V1, "user", "pass")
        // Should not throw
        Json.parseToJsonElement(result)
    }

    @Test
    fun `output is valid JSON for LOWER_CASE_V2`() {
        val result = createLoginModel(Json, LoginKeyStyle.LOWER_CASE_V2, "user", "pass")
        // Should not throw
        Json.parseToJsonElement(result)
    }

    @Test
    fun `output contains exactly two fields`() {
        for (style in LoginKeyStyle.entries) {
            val result = createLoginModel(Json, style, "user", "pass")
            val obj = Json.parseToJsonElement(result).jsonObject
            assertEquals(2, obj.size, "Should contain exactly 2 fields for $style")
        }
    }

    //endregion

    //region Json configuration variants

    @Test
    fun `works with lenient Json`() {
        val lenientJson = Json { isLenient = true }
        val result = createLoginModel(lenientJson, LoginKeyStyle.LOWER_CASE_V2, "user", "pass")
        lenientJson.parseToJsonElement(result)
    }

    @Test
    fun `works with strict Json`() {
        val strictJson = Json { isLenient = false }
        val result = createLoginModel(strictJson, LoginKeyStyle.LOWER_CASE_V2, "user", "pass")
        strictJson.parseToJsonElement(result)
    }

    @Test
    fun `works with ignoreUnknownKeys Json`() {
        val json = Json { ignoreUnknownKeys = true }
        val result = createLoginModel(json, LoginKeyStyle.CAMEL_CASE_V1, "user", "pass")
        json.parseToJsonElement(result)
    }

    //endregion
}
