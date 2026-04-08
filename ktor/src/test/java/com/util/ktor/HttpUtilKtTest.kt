package com.util.ktor

import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.model.CustomResultCode
import com.util.ktor.model.ResultModel
import com.util.ktor.plugin.AuthRefreshLock
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.plugin
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.koin.dsl.module
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpUtilKtTest : KoinTest {

    private val mockConfigProvider = object : NetworkConfigProvider {
        override val serverAddress: String = "http://192.168.0.8"
        override val serverPort: String = "8085"
        override var token: String = ""
        override val loginPath: String = "/morningCheck/user/login"
        override val uploadFilePath: String = "/uploadMinio"
        override val checkUpdatePath: String = ""
        override val heartBeatPath: String = "/heart"
        override val bucketName: String = "morningcheck"
        override val username: String = "demo"
        override val password: String = "123456"
        override val tenant: String = ""
        override fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.CAMEL_CASE_V1
        override fun onNewTokenReceived(newToken: String, newTenant: String?) {}
    }

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        printLogger()
        modules(
            module {
                single<NetworkConfigProvider> { mockConfigProvider }
                single { Json { ignoreUnknownKeys = true } }
            }
        )
    }

    @Before
    fun setUp() {
        mockConfigProvider.token = ""
    }

    @After
    fun tearDown() {
        mockConfigProvider.token = ""
    }

    @Test
    fun `createLoginModel with CAMEL CASE V1 style`() {
        val configProvider: NetworkConfigProvider = get()
        val json: Json = get()
        val loginModel = createLoginModel(
            json,
            LoginKeyStyle.CAMEL_CASE_V1,
            configProvider.username,
            configProvider.password
        )
        val jsonObject = json.parseToJsonElement(loginModel).jsonObject
        assertTrue(jsonObject.containsKey("userName"))
        assertEquals(configProvider.username, jsonObject["userName"]?.jsonPrimitive?.content)
        assertTrue(jsonObject.containsKey("passWord"))
        assertEquals(configProvider.password, jsonObject["passWord"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createLoginModel with LOWER CASE V2 style`() {
        val configProvider: NetworkConfigProvider = get()
        val json: Json = get()
        val loginModel = createLoginModel(
            json,
            LoginKeyStyle.LOWER_CASE_V2,
            configProvider.username,
            configProvider.password
        )
        val jsonObject = json.parseToJsonElement(loginModel).jsonObject
        assertTrue(jsonObject.containsKey("username"))
        assertEquals(configProvider.username, jsonObject["username"]?.jsonPrimitive?.content)
        assertTrue(jsonObject.containsKey("password"))
        assertEquals(configProvider.password, jsonObject["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createLoginModel with empty username and password`() {
        val json: Json = get()
        val username = ""
        val password = ""

        val loginModelV1 =
            createLoginModel(json, LoginKeyStyle.CAMEL_CASE_V1, username, password)
        val jsonObjectV1 = json.parseToJsonElement(loginModelV1).jsonObject
        assertTrue(jsonObjectV1.containsKey("userName"))
        assertEquals(username, jsonObjectV1["userName"]?.jsonPrimitive?.content)
        assertTrue(jsonObjectV1.containsKey("passWord"))
        assertEquals(password, jsonObjectV1["passWord"]?.jsonPrimitive?.content)

        val loginModelV2 =
            createLoginModel(json, LoginKeyStyle.LOWER_CASE_V2, username, password)
        val jsonObjectV2 = json.parseToJsonElement(loginModelV2).jsonObject
        assertTrue(jsonObjectV2.containsKey("username"))
        assertEquals(username, jsonObjectV2["username"]?.jsonPrimitive?.content)
        assertTrue(jsonObjectV2.containsKey("password"))
        assertEquals(password, jsonObjectV2["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createLoginModel with special characters in username and password`() {
        val json = Json
        val username = "user\"with\\special'characters\uD83D\uDE00"
        val password = "pass\"with\\special'characters\uD83E\uDD13"

        val loginModelV1 =
            createLoginModel(json, LoginKeyStyle.CAMEL_CASE_V1, username, password)
        val jsonObjectV1 = json.parseToJsonElement(loginModelV1).jsonObject
        assertTrue(jsonObjectV1.containsKey("userName"))
        assertEquals(username, jsonObjectV1["userName"]?.jsonPrimitive?.content)
        assertTrue(jsonObjectV1.containsKey("passWord"))
        assertEquals(password, jsonObjectV1["passWord"]?.jsonPrimitive?.content)

        val loginModelV2 =
            createLoginModel(json, LoginKeyStyle.LOWER_CASE_V2, username, password)
        val jsonObjectV2 = json.parseToJsonElement(loginModelV2).jsonObject
        assertTrue(jsonObjectV2.containsKey("username"))
        assertEquals(username, jsonObjectV2["username"]?.jsonPrimitive?.content)
        assertTrue(jsonObjectV2.containsKey("password"))
        assertEquals(password, jsonObjectV2["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createLoginModel with non-lenient json`() {
        val nonLenientJson = Json { isLenient = false }
        createLoginModel(nonLenientJson, LoginKeyStyle.LOWER_CASE_V2, "user", "pass")
    }

    @Test
    fun `createDefaultHttpClient creates client without error`() {
        val client = createDefaultHttpClient(get(), get())
        assertNotNull(client)
        client.close()
    }

    @Serializable
    data class TestData(val id: Int, val message: String)

    @Test
    fun `ContentNegotiation plugin should serialize request and deserialize response`() =
        runBlocking {
            val requestObject = TestData(id = 1, message = "Hello Ktor")
            val expectedJsonResponse = """
                {
                    "id": 2,
                    "message": "Hello from Mock Server"
                }
            """.trimIndent()

            val mockEngine = MockEngine { request ->
                val requestBodyText = request.body.toByteArray().decodeToString()
                val expectedRequestBody =
                    """{"id":1,"message":"Hello Ktor"}""".trimIndent()
                assertEquals(expectedRequestBody, requestBodyText)

                respond(
                    content = expectedJsonResponse,
                    status = HttpStatusCode.OK,
                    headers = headersOf(
                        HttpHeaders.ContentType,
                        "application/json"
                    )
                )
            }

            val client = HttpClient(mockEngine) {
                install(ContentNegotiation) {
                    json(get())
                }
            }

            val responseObject: TestData =
                client.post("http://localhost/test") {
                    setBody(requestObject)
                    contentType(ContentType.Application.Json)
                }.body()

            assertEquals(2, responseObject.id)
            assertEquals("Hello from Mock Server", responseObject.message)
            client.close()
        }

    @Test
    fun `createDefaultHttpClient CustomAuthTriggerPlugin installation`() {
        val mockEngine = MockEngine { respondOk() }
        val client = createTestHttpClient(mockEngine, get(), get())
        assertNotNull(client)
        client.close()
    }

    @Test
    fun `createDefaultHttpClient HttpTimeout plugin installation and default values`() {
        val client = createDefaultHttpClient(get(), get())
        val timeoutFeature = client.plugin(HttpTimeout)
        assertNotNull(timeoutFeature)
        client.close()
    }

    @Test
    fun `Auth plugin BearerTokens loading with token`() = runBlocking {
        val config: NetworkConfigProvider = get()
        config.token = "my-secret-token"
        val mockEngine = MockEngine { request ->
            val authHeader = request.headers[HttpHeaders.Authorization]
            assertEquals("Bearer my-secret-token", authHeader)
            respondOk()
        }
        val client = createTestHttpClient(mockEngine, config, get())

        client.get("/some/protected/path")

        mockEngine.close()
        client.close()
    }

    private fun createTestHttpClient(
        engine: MockEngine,
        config: NetworkConfigProvider,
        json: Json,
    ): HttpClient {
        return HttpClient(engine) {
            installPlugins(config, json)
        }
    }

    @Test
    fun `Auth plugin BearerTokens loading without token`() = runBlocking {
        val config = mockConfigProvider
        config.token = ""
        val mockEngine = MockEngine { request ->
            assertFalse(request.headers.contains(HttpHeaders.Authorization))
            respondOk()
        }
        val client = createTestHttpClient(mockEngine, config, get())
        client.get("/some/path")
        mockEngine.close()
        client.close()
    }

    @Test
    fun `Auth plugin sendWithoutRequest logic`() = runBlocking {
        val config: NetworkConfigProvider = get()
        config.token = "my-token"
        val mockEngine = MockEngine { request ->
            if (request.url.encodedPath == config.loginPath) {
                assertFalse(
                    request.headers.contains(HttpHeaders.Authorization),
                    "Token should not be sent to login path"
                )
            } else {
                assertTrue(
                    request.headers.contains(HttpHeaders.Authorization),
                    "Token should be sent to other paths"
                )
            }
            respondOk()
        }
        val client = createTestHttpClient(mockEngine, config, get())

        client.get(config.loginPath)
        client.get("/other/path")

        mockEngine.close()
        client.close()
    }

    @Test
    fun `handleException returns TIMEOUT_ERROR for SerializationException`() {
        val mockEngine = MockEngine { respondOk() }
        val httpUtil = HttpUtil(
            httpClient = HttpClient(mockEngine),
            json = get(),
            config = mockConfigProvider
        )
        val result =
            httpUtil.handleException<String>(SerializationException("parse error"))
        assertEquals(CustomResultCode.SERIALIZATION_ERROR, result.code)
        assertTrue(result.message?.contains("parse error") == true)
        mockEngine.close()
    }

    @Test
    fun `handleException returns UNKNOWN_ERROR for unknown exception`() {
        val mockEngine = MockEngine { respondOk() }
        val httpUtil = HttpUtil(
            httpClient = HttpClient(mockEngine),
            json = get(),
            config = mockConfigProvider
        )
        val result =
            httpUtil.handleException<String>(RuntimeException("something went wrong"))
        assertEquals(CustomResultCode.UNKNOWN_ERROR, result.code)
        assertTrue(result.message?.contains("something went wrong") == true)
        mockEngine.close()
    }

    @Test
    fun `ResultModel isSuccess returns true for code 200`() {
        val model: ResultModel<String> = ResultModel.success("data")
        assertTrue(model.isSuccess())
        assertFalse(model.isError())
    }

    @Test
    fun `ResultModel isError returns true for non-200 code`() {
        val model: ResultModel<String> = ResultModel.error("fail")
        assertTrue(model.isError())
        assertFalse(model.isSuccess())
    }

    @Test
    fun `AuthRefreshLock is independent per instance`() {
        val lock1 = AuthRefreshLock()
        val lock2 = AuthRefreshLock()
        assert(lock1 !== lock2)
        assert(lock1.mutex !== lock2.mutex)
    }

    @Test
    fun `Auth plugin with shared AuthRefreshLock`() = runBlocking {
        val config: NetworkConfigProvider = get()
        config.token = "shared-lock-token"
        val sharedLock = AuthRefreshLock()

        val mockEngine1 = MockEngine { request ->
            assertEquals("Bearer shared-lock-token", request.headers[HttpHeaders.Authorization])
            respondOk()
        }
        val mockEngine2 = MockEngine { request ->
            assertEquals("Bearer shared-lock-token", request.headers[HttpHeaders.Authorization])
            respondOk()
        }

        val client1 = HttpClient(mockEngine1) {
            installPlugins(config, get(), sharedLock)
        }
        val client2 = HttpClient(mockEngine2) {
            installPlugins(config, get(), sharedLock)
        }

        client1.get("/path1")
        client2.get("/path2")

        mockEngine1.close()
        mockEngine2.close()
        client1.close()
        client2.close()
    }
}
