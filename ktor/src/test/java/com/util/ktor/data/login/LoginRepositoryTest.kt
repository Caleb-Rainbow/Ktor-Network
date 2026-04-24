package com.util.ktor.data.login

import com.util.ktor.HttpUtil
import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.login.model.UserToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginRepositoryTest {

    private lateinit var json: Json
    private lateinit var config: NetworkConfigProvider

    @Before
    fun setUp() {
        json = Json { ignoreUnknownKeys = true }
        config = object : NetworkConfigProvider {
            override val serverAddress: String = "http://localhost"
            override val serverPort: String = "8080"
            override var token: String = ""
            override val loginPath: String = "/api/login"
            override val uploadFilePath: String = "/upload"
            override val checkUpdatePath: String = "/api/version"
            override val heartBeatPath: String = "/heart"
            override val bucketName: String = "test"
            override val username: String = "admin"
            override val password: String = "password"
            override val tenant: String = ""
            override val getLogoPath: String = "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo"
            override fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.CAMEL_CASE_V1
            override fun onNewTokenReceived(newToken: String, newTenant: String?) {}
        }
    }

    private fun createLoginRepo(engine: MockEngine): LoginRepository {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val httpUtil = HttpUtil(httpClient = client, json = json, config = config)
        return LoginRepository(httpUtil, json, config)
    }

    //region 四种响应格式测试

    @Test
    fun `格式A - data 是 UserToken 对象`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"code":200,"msg":"操作成功","data":{"token":"jwt-token-123","officeId":42,"tenant":"prod"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createLoginRepo(engine)
        val result = repo.passwordLogin(username = "admin", password = "123456")

        assertTrue(result.isSuccess())
        val token = result.data!!
        assertEquals("jwt-token-123", token.token)
        assertEquals(42, token.officeId)
        assertEquals("prod", token.tenant)
    }

    @Test
    fun `格式B - data 是纯字符串 token`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"code":200,"msg":"操作成功","data":"eyJhbGciOiJIUzI1NiJ9"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createLoginRepo(engine)
        val result = repo.passwordLogin(username = "admin", password = "123456")

        assertTrue(result.isSuccess())
        val token = result.data!!
        assertEquals("eyJhbGciOiJIUzI1NiJ9", token.token)
        assertNull(token.officeId)
        assertNull(token.tenant)
    }

    @Test
    fun `格式C - 顶层 token 是对象`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"msg":"操作成功","code":200,"deptId":2,"tenant":"dev","token":{"tenant":"dev","userId":381,"deptId":2,"token":"eyJhbG"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createLoginRepo(engine)
        val result = repo.passwordLogin(username = "admin", password = "123456")

        assertTrue(result.isSuccess())
        val token = result.data!!
        assertEquals("eyJhbG", token.token)
        assertEquals(2, token.officeId)
        assertEquals("dev", token.tenant)
    }

    @Test
    fun `格式D - 顶层 token 是纯字符串`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"msg":"操作成功","code":200,"deptId":2,"tenant":"dev","token":"eyJhbG"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createLoginRepo(engine)
        val result = repo.passwordLogin(username = "admin", password = "123456")

        assertTrue(result.isSuccess())
        val token = result.data!!
        assertEquals("eyJhbG", token.token)
        assertNull(token.officeId)
        assertNull(token.tenant)
    }

    //endregion

    //region 请求格式适配

    @Test
    fun `CAMEL_CASE_V1 发送正确的请求体 key`() = runTest {
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            assert(body.contains("userName"))
            assert(body.contains("passWord"))
            respond(
                content = """{"code":200,"msg":"ok","data":{"token":"jwt-token-123"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createLoginRepo(engine)
        val result = repo.passwordLogin(username = "admin", password = "123456")
        assertNotNull(result)
    }

    @Test
    fun `LOWER_CASE_V2 发送正确的请求体 key`() = runTest {
        val v2Config = object : NetworkConfigProvider by config {
            override fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.LOWER_CASE_V2
        }
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            assert(body.contains("username"))
            assert(body.contains("password"))
            respond(
                content = """{"code":200,"msg":"ok","data":{"token":"jwt-token-456"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val client = HttpClient(engine) { install(ContentNegotiation) { json(json) } }
        val httpUtil = HttpUtil(httpClient = client, json = json, config = v2Config)
        val repo = LoginRepository(httpUtil, json, v2Config)
        val result = repo.passwordLogin(username = "user", password = "pass")
        assertNotNull(result)
    }

    @Test
    fun `自定义 host 拼接到请求路径`() = runTest {
        val engine = MockEngine { request ->
            assert(request.url.toString().contains("custom-host"))
            respond(
                content = """{"code":200,"msg":"ok","data":{"token":"abc"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createLoginRepo(engine)
        val result = repo.passwordLogin(host = "custom-host", username = "u", password = "p")
        assertNotNull(result)
    }

    //endregion

    //region 错误场景

    @Test
    fun `登录失败时返回错误码和消息`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"code":401,"msg":"用户名或密码错误"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createLoginRepo(engine)
        val result = repo.passwordLogin(username = "admin", password = "wrong")

        assertTrue(result.isError())
        assertEquals(401, result.code)
        assertEquals("用户名或密码错误", result.message)
        assertNull(result.data)
    }

    @Test
    fun `格式C 登录失败时返回错误码和消息`() = runTest {
        val engine = MockEngine { request ->
            respond(
                content = """{"code":500,"msg":"服务器错误","token":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createLoginRepo(engine)
        val result = repo.passwordLogin(username = "admin", password = "123456")

        assertTrue(result.isError())
        assertEquals(500, result.code)
        assertNull(result.data)
    }

    //endregion

    //region UserToken 序列化测试

    @Test
    fun `UserToken 所有字段反序列化`() {
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
    fun `UserToken deptId 别名映射到 officeId`() {
        val jsonStr = """{"token":"t","deptId":99}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals(99, token.officeId)
    }

    @Test
    fun `UserToken 仅 token 字段`() {
        val jsonStr = """{"token":"minimal"}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals("minimal", token.token)
        assertNull(token.officeId)
        assertNull(token.tenant)
        assertNull(token.forceChangePassword)
        assertNull(token.forceChangeReason)
    }

    @Test
    fun `UserToken 往返序列化`() {
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

    //endregion
}
