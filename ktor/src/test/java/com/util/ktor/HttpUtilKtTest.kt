package com.util.ktor

import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.file.FileRepository
import com.util.ktor.data.heart.HeartRepository
import com.util.ktor.data.login.LoginRepository
import com.util.ktor.data.personalization.PersonalizationRepository
import com.util.ktor.data.version.VersionRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIOEngineConfig
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    val mockConfigProvider = object : NetworkConfigProvider {
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
        // 将 Koin 日志打印到控制台
        printLogger()
        // 在这里声明您的模块
        modules(
            module {
                // 提供一个 NetworkConfigProvider 的单例
                single<NetworkConfigProvider> { mockConfigProvider }
                // 提供一个 Json 的单例
                single { Json { ignoreUnknownKeys = true } }
                single { HttpUtil(httpClient = createDefaultHttpClient(get(), get()), json = get(), config = get()) }
                single { VersionRepository(httpUtil = get(), config = get()) }
                single { FileRepository(httpUtil = get(), config = get()) }
                single { LoginRepository(json = get(), httpUtil = get(), config = get()) }
                single { HeartRepository(httpUtil = get(), config = get()) }
                single { PersonalizationRepository(httpUtil = get(), config = get()) }
            }
        )
    }

    @Test
    fun `createLoginModel with CAMEL CASE V1 style`() {
        val configProvider: NetworkConfigProvider = get()
        val json: Json = get()
        val loginModel = createLoginModel(json, LoginKeyStyle.CAMEL_CASE_V1, configProvider.username, configProvider.password)
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
        val loginModel = createLoginModel(json, LoginKeyStyle.LOWER_CASE_V2, configProvider.username, configProvider.password)
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

        // Test with CAMEL_CASE_V1
        val loginModelV1 = createLoginModel(json, LoginKeyStyle.CAMEL_CASE_V1, username, password)
        val jsonObjectV1 = json.parseToJsonElement(loginModelV1).jsonObject
        assertTrue(jsonObjectV1.containsKey("userName"))
        assertEquals(username, jsonObjectV1["userName"]?.jsonPrimitive?.content)
        assertTrue(jsonObjectV1.containsKey("passWord"))
        assertEquals(password, jsonObjectV1["passWord"]?.jsonPrimitive?.content)

        // Test with LOWER_CASE_V2
        val loginModelV2 = createLoginModel(json, LoginKeyStyle.LOWER_CASE_V2, username, password)
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

        // Test with CAMEL_CASE_V1
        val loginModelV1 = createLoginModel(json, LoginKeyStyle.CAMEL_CASE_V1, username, password)
        val jsonObjectV1 = json.parseToJsonElement(loginModelV1).jsonObject
        assertTrue(jsonObjectV1.containsKey("userName"))
        assertEquals(username, jsonObjectV1["userName"]?.jsonPrimitive?.content)
        assertTrue(jsonObjectV1.containsKey("passWord"))
        assertEquals(password, jsonObjectV1["passWord"]?.jsonPrimitive?.content)

        // Test with LOWER_CASE_V2
        val loginModelV2 = createLoginModel(json, LoginKeyStyle.LOWER_CASE_V2, username, password)
        val jsonObjectV2 = json.parseToJsonElement(loginModelV2).jsonObject
        assertTrue(jsonObjectV2.containsKey("username"))
        assertEquals(username, jsonObjectV2["username"]?.jsonPrimitive?.content)
        assertTrue(jsonObjectV2.containsKey("password"))
        assertEquals(password, jsonObjectV2["password"]?.jsonPrimitive?.content)
    }

    @Test
    fun `createLoginModel with null json object`() {
        // `json: Json` 参数不可为空。
        // 但是，我们可以使用配置为非宽松的 Json 实例进行测试，
        // 如果处理不当，可能会对某些输入抛出 SerializationException，
        // 尽管 createLoginModel 本身会构造一个已知的有效 JSON 字符串。
        // 由于 Kotlin 的空安全性，Json 对象本身的直接 NullPointerException 是不可测试的。
        // 此测试主要确保，如果配置错误的 Json 对象导致问题，
        // 它可能会表现为 SerializationException。
        val nonLenientJson = Json { isLenient = false }
        // Expect no exception as the created model is internally valid.
        createLoginModel(nonLenientJson, LoginKeyStyle.LOWER_CASE_V2, "user", "pass")
    }

    @Test
    fun `createDefaultHttpClient basic configuration`() {
        val client = createDefaultHttpClient(get(), get())
        assertNotNull(client)
        println(client.engine.config::class)
        println(CIOEngineConfig::class)
        client.close()
    }

    @Serializable
    data class TestData(val id: Int, val message: String)

    @Test
    fun `ContentNegotiation plugin should serialize request and deserialize response`() = runBlocking {
        val requestObject = TestData(id = 1, message = "Hello Ktor")
        val expectedJsonResponse = """
    {
        "id": 2,
        "message": "Hello from Mock Server"
    }
    """.trimIndent()

        // 2. 创建并配置 MockEngine
        // MockEngine 会拦截所有发出的请求
        val mockEngine = MockEngine { request ->
            // 验证请求体是否被正确序列化
            val requestBodyText = request.body.toByteArray().decodeToString()
            // 注意：因为 prettyPrint = true，所以 JSON 会被格式化
            val expectedRequestBody = """{"id":1,"message":"Hello Ktor"}""".trimIndent()
            assertEquals(expectedRequestBody, requestBodyText)

            // 构造并返回一个模拟的响应
            respond(
                content = expectedJsonResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        // 3. 创建一个使用 MockEngine 和 ContentNegotiation 的 HttpClient
        // 注意：我们在这里手动创建客户端，而不是使用 createDefaultHttpClient，
        // 因为我们需要注入我们自己的 MockEngine。
        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(get()) // 确保客户端使用与我们预期相同的 Json 实例
            }
        }

        // 4. 执行请求并验证
        val responseObject: TestData = client.post("http://localhost/test") {
            setBody(requestObject) // 这里会触发序列化
            contentType(ContentType.Application.Json)
        }.body() // 这里会触发反序列化

        // 验证响应是否被正确反序列化
        assertEquals(2, responseObject.id)
        assertEquals("Hello from Mock Server", responseObject.message)

        client.close()
    }

    @Test
    fun `createDefaultHttpClient CustomAuthTriggerPlugin installation`() {
        // 由于 CustomAuthTriggerPlugin 是自定义插件，我们无法直接检查其实例类型。
        // 但我们可以验证 HttpClient 是否能成功创建，这间接表明插件的 install 块没有抛出异常。
        // 这是一个存在性测试。
        val client = createDefaultHttpClient(get(), get())
        assertNotNull(client)
        // 更深入的测试需要了解 CustomAuthTriggerPlugin 的内部工作原理。
        // 例如，如果它注册了一个钩子，我们可以尝试触发那个钩子。
        // 目前，我们确认它没有破坏客户端的构建。
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
    fun `createDefaultHttpClient Auth plugin installation BearerTokens loading with token`() = runBlocking {
        val config: NetworkConfigProvider = get()
        config.token = "my-secret-token"
        val mockEngine = MockEngine { request ->
            // 验证 Authorization 头是否已添加
            val authHeader = request.headers[HttpHeaders.Authorization]
            assertEquals("Bearer my-secret-token", authHeader)
            respondOk()
        }
        val client = createDefaultHttpClient(config, get())

        // 对一个非登录路径发起请求
        client.get("/some/protected/path")

        mockEngine.close()
        client.close()
    }

    fun createTestHttpClient(engine: MockEngine, config: NetworkConfigProvider, json: Json): HttpClient {
        return HttpClient(engine) {
            installPlugins(config, json)
        }
    }

    @Test
    fun `createDefaultHttpClient Auth plugin installation BearerTokens loading without token`() = runBlocking {
        val config = mockConfigProvider
        config.token = ""
        val mockEngine = MockEngine { request ->
            // 验证 Authorization 头是否未添加
            assertFalse(request.headers.contains(HttpHeaders.Authorization))
            respondOk()
        }
        val client = createTestHttpClient(mockEngine, config, get())
        client.get("/some/path")
        mockEngine.close()
        client.close()
    }

    @Test
    fun `createDefaultHttpClient Auth plugin sendWithoutRequest logic`() = runBlocking {
        val config: NetworkConfigProvider = get()
        config.token = "my-token"
        val mockEngine = MockEngine { request ->
            // 根据请求路径验证 Authorization 头的存在性
            if (request.url.encodedPath == config.loginPath) {
                assertFalse(request.headers.contains(HttpHeaders.Authorization), "Token should not be sent to login path")
            } else {
                assertTrue(request.headers.contains(HttpHeaders.Authorization), "Token should be sent to other paths")
            }
            respondOk()
        }
        val client = createTestHttpClient(mockEngine, config, get())

        // 请求登录路径
        client.get(config.loginPath)
        // 请求其他路径
        client.get("/other/path")

        mockEngine.close()
        client.close()
    }
}
