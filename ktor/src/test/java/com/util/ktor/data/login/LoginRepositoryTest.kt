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

    // Note: LoginRepository.passwordLogin calls HttpUtil.post which uses
    // android.util.Log for error handling. Tests verify request construction.

    @Test
    fun `passwordLogin with CAMEL_CASE_V1 sends correct body keys`() = runTest {
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
    fun `passwordLogin with LOWER_CASE_V2 sends correct body keys`() = runTest {
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
    fun `passwordLogin with custom host`() = runTest {
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

    @Test
    fun `passwordLogin with special characters in credentials`() = runTest {
        val engine = MockEngine { request ->
            val body = request.body.toByteArray().decodeToString()
            assert(body.isNotEmpty())
            respond(
                content = """{"code":200,"msg":"ok","data":{"token":"special-token"}}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }
        val repo = createLoginRepo(engine)
        val result = repo.passwordLogin(
            username = "user@domain.com",
            password = "p@ss!w0rd#$%"
        )
        assertNotNull(result)
    }

    //region UserToken model tests (pure serialization, no android.util.Log)

    @Test
    fun `UserToken with all fields`() {
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
    fun `UserToken with deptId alias maps to officeId`() {
        val jsonStr = """{"token":"t","deptId":99}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals(99, token.officeId)
    }

    @Test
    fun `UserToken with only token`() {
        val jsonStr = """{"token":"minimal"}"""
        val token = json.decodeFromString(UserToken.serializer(), jsonStr)
        assertEquals("minimal", token.token)
        assertEquals(null, token.officeId)
        assertEquals(null, token.tenant)
        assertEquals(null, token.forceChangePassword)
        assertEquals(null, token.forceChangeReason)
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

    //endregion
}
