package com.util.ktor.data.login

import com.util.ktor.data.login.model.UserToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LoginResponseParserTest {

    private lateinit var json: Json

    @Before
    fun setUp() {
        json = Json { ignoreUnknownKeys = true }
    }

    private fun parse(jsonStr: String): UserToken? {
        val obj: JsonObject = Json.decodeFromString(jsonStr)
        return parseLoginUserToken(json, obj)
    }

    @Test
    fun `格式A - data 是 UserToken 对象，包含所有字段`() {
        val token = parse("""{"code":200,"msg":"ok","data":{"token":"jwt-123","officeId":1,"tenant":"prod"}}""")
        assertNotNull(token)
        assertEquals("jwt-123", token.token)
        assertEquals(1, token.officeId)
        assertEquals("prod", token.tenant)
    }

    @Test
    fun `格式A - data 是 UserToken 对象，使用 deptId 别名`() {
        val token = parse("""{"code":200,"msg":"ok","data":{"token":"jwt-123","deptId":5}}""")
        assertNotNull(token)
        assertEquals("jwt-123", token.token)
        assertEquals(5, token.officeId)
    }

    @Test
    fun `格式B - data 是纯字符串`() {
        val token = parse("""{"code":200,"msg":"ok","data":"eyJhbGciOiJIUzI1NiJ9"}""")
        assertNotNull(token)
        assertEquals("eyJhbGciOiJIUzI1NiJ9", token.token)
        assertNull(token.officeId)
        assertNull(token.tenant)
    }

    @Test
    fun `格式C - 顶层 token 是对象，包含 userId 等未知字段被忽略`() {
        val token = parse("""{"msg":"操作成功","code":200,"deptId":2,"tenant":"dev","token":{"tenant":"dev","userId":381,"deptId":2,"token":"eyJhbG"}}""")
        assertNotNull(token)
        assertEquals("eyJhbG", token.token)
        assertEquals(2, token.officeId)
        assertEquals("dev", token.tenant)
    }

    @Test
    fun `格式D - 顶层 token 是纯字符串`() {
        val token = parse("""{"msg":"操作成功","code":200,"deptId":2,"tenant":"dev","token":"eyJhbG"}""")
        assertNotNull(token)
        assertEquals("eyJhbG", token.token)
        assertNull(token.officeId)
        assertNull(token.tenant)
    }

    @Test
    fun `无 token 也无 data 时返回 null`() {
        val token = parse("""{"code":200,"msg":"ok"}""")
        assertNull(token)
    }

    @Test
    fun `token 字段优先于 data 字段`() {
        val token = parse("""{"code":200,"msg":"ok","token":"from-token","data":{"token":"from-data"}}""")
        assertNotNull(token)
        assertEquals("from-token", token.token)
    }
}
