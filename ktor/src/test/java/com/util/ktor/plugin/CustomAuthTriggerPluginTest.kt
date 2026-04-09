package com.util.ktor.plugin

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals

class CustomAuthTriggerPluginTest {

    private lateinit var json: Json

    @Before
    fun setUp() {
        json = Json { ignoreUnknownKeys = true }
    }

    private fun createClient(
        engine: MockEngine,
        tokenExpiredCode: Int = 401,
    ): HttpClient {
        return HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
            install(CustomAuthTriggerPlugin) {
                this.json = this@CustomAuthTriggerPluginTest.json
                this.tokenExpiredCode = tokenExpiredCode
            }
        }
    }

    @Test
    fun `rewrites HTTP 200 with token expired code to 401`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"code":401,"msg":"token过期","data":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)
        val response = client.get("http://localhost/api/data")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        mockEngine.close()
        client.close()
    }

    @Test
    fun `does not rewrite HTTP 200 with normal code`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"code":200,"msg":"ok","data":"success"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)
        val response = client.get("http://localhost/api/data")
        assertEquals(HttpStatusCode.OK, response.status)
        mockEngine.close()
        client.close()
    }

    @Test
    fun `does not rewrite non-JSON response`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = "plain text response",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Text.Plain.toString())
            )
        }
        val client = createClient(mockEngine)
        val response = client.get("http://localhost/api/data")
        assertEquals(HttpStatusCode.OK, response.status)
        mockEngine.close()
        client.close()
    }

    @Test
    fun `preserves body when rewriting to 401`() = runTest {
        val originalBody = """{"code":401,"msg":"token过期","data":null}"""
        val mockEngine = MockEngine {
            respond(
                content = originalBody,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)
        val response = client.get("http://localhost/api/data")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val body = response.bodyAsText()
        assertEquals(originalBody, body)
        mockEngine.close()
        client.close()
    }

    @Test
    fun `custom tokenExpiredCode triggers rewrite`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"code":999,"msg":"session expired","data":null}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine, tokenExpiredCode = 999)
        val response = client.get("http://localhost/api/data")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        mockEngine.close()
        client.close()
    }

    @Test
    fun `does not rewrite when code field is missing`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"msg":"ok","data":"value"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)
        val response = client.get("http://localhost/api/data")
        assertEquals(HttpStatusCode.OK, response.status)
        mockEngine.close()
        client.close()
    }

    @Test
    fun `does not rewrite when code field is not a number`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """{"code":"not-a-number","msg":"ok"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            )
        }
        val client = createClient(mockEngine)
        val response = client.get("http://localhost/api/data")
        assertEquals(HttpStatusCode.OK, response.status)
        mockEngine.close()
        client.close()
    }

    @Test
    fun `plugin config has default tokenExpiredCode 401`() {
        val config = CustomAuthTriggerPlugin.Config()
        assertEquals(401, config.tokenExpiredCode)
    }

    @Test
    fun `AuthRefreshLock withLock is accessible`() = runTest {
        val lock = AuthRefreshLock()
        val result = lock.withLock { 42 }
        assertEquals(42, result)
    }
}
