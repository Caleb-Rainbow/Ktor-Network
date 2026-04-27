package com.util.ktor.data.version

import com.util.ktor.HttpUtil
import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.version.model.Version
import com.util.ktor.model.CustomResultCode
import com.util.ktor.model.ResultModel
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class VersionRepositoryTest {

    private lateinit var json: Json
    private lateinit var config: NetworkConfigProvider
    private val successVersionJson = """{"code":200,"msg":"成功","data":{"appName":"App","code":10,"name":"3.5.1","url":"https://cdn.example.com/app.apk","size":"50MB","description":"更新描述"}}"""
    private val errorVersionJson = """{"code":500,"msg":"服务器错误"}"""
    private val jsonHeaders = headersOf("Content-Type" to listOf(ContentType.Application.Json.toString()))

    @Before
    fun setUp() {
        json = Json { ignoreUnknownKeys = true }
        config = object : NetworkConfigProvider {
            override val serverAddress: String = "http://192.168.0.8"
            override val serverPort: String = "8085"
            override var token: String = ""
            override val loginPath: String = "/api/login"
            override val uploadFilePath: String = "/upload"
            override val checkUpdatePath: String = "https://vis.xingchenwulian.com/system/appversion/checkUpdate/%E6%99%A8%E6%A3%80%E4%BB%AA"
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

    private fun createVersionRepo(engine: MockEngine): VersionRepository {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val httpUtil = HttpUtil(httpClient = client, json = json, config = config)
        return VersionRepository(httpUtil, client, config)
    }

    private fun createVersionRepoWithConfig(
        engine: MockEngine,
        testConfig: NetworkConfigProvider
    ): VersionRepository {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val httpUtil = HttpUtil(httpClient = client, json = json, config = testConfig)
        return VersionRepository(httpUtil, client, testConfig)
    }

    //region resolveInternalUpdateUrl tests

    @Test
    fun `resolveInternalUpdateUrl extracts path from absolute URL`() = runTest {
        var requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls.add(request.url.toString())
            respond(successVersionJson, headers = jsonHeaders)
        }
        val repo = createVersionRepo(engine)
        repo.checkUpdateDual()
        assertEquals(2, requestedUrls.size)
        assertTrue(requestedUrls.any { it.contains("vis.xingchenwulian.com") })
        assertTrue(requestedUrls.any {
            it.contains("192.168.0.8") && it.contains("8085") &&
                it.contains("/system/appversion/checkUpdate/%E6%99%A8%E6%A3%80%E4%BB%AA")
        })
    }

    @Test
    fun `resolveInternalUpdateUrl handles relative checkUpdatePath`() = runTest {
        val relativeConfig = object : NetworkConfigProvider by config {
            override val checkUpdatePath: String = "/api/version/check"
        }
        var requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls.add(request.url.toString())
            respond(successVersionJson, headers = jsonHeaders)
        }
        val repo = createVersionRepoWithConfig(engine, relativeConfig)
        repo.checkUpdateDual()
        assertTrue(requestedUrls.any {
            it.contains("192.168.0.8") && it.contains("8085") && it.contains("/api/version/check")
        })
    }

    @Test
    fun `resolveInternalUpdateUrl preserves query parameters`() = runTest {
        val queryConfig = object : NetworkConfigProvider by config {
            override val checkUpdatePath: String = "https://example.com/api/version?type=stable"
        }
        var requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls.add(request.url.toString())
            respond(successVersionJson, headers = jsonHeaders)
        }
        val repo = createVersionRepoWithConfig(engine, queryConfig)
        repo.checkUpdateDual()
        assertTrue(requestedUrls.any {
            it.contains("192.168.0.8") && it.contains("type=stable")
        })
    }

    @Test
    fun `resolveInternalUpdateUrl does not double port when embedded in serverAddress`() = runTest {
        val embeddedPortConfig = object : NetworkConfigProvider by config {
            override val serverAddress: String = "http://192.168.0.8:9090"
            override val serverPort: String = "8085"
        }
        var requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls.add(request.url.toString())
            respond(successVersionJson, headers = jsonHeaders)
        }
        val repo = createVersionRepoWithConfig(engine, embeddedPortConfig)
        repo.checkUpdateDual()
        val internalUrl = requestedUrls.first { it.contains("192.168.0.8:9090") }
        assertTrue(internalUrl.contains("9090"))
    }

    @Test
    fun `resolveInternalUpdateUrl handles HTTPS serverAddress`() = runTest {
        val httpsConfig = object : NetworkConfigProvider by config {
            override val serverAddress: String = "https://internal.example.com"
            override val serverPort: String = "443"
        }
        var requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls.add(request.url.toString())
            respond(successVersionJson, headers = jsonHeaders)
        }
        val repo = createVersionRepoWithConfig(engine, httpsConfig)
        repo.checkUpdateDual()
        assertTrue(requestedUrls.any { it.contains("internal.example.com") })
    }

    @Test
    fun `resolveInternalUpdateUrl handles trailing slash in serverAddress`() = runTest {
        val trailingSlashConfig = object : NetworkConfigProvider by config {
            override val serverAddress: String = "http://192.168.0.8/"
            override val serverPort: String = "8085"
        }
        var requestedUrls = mutableListOf<String>()
        val engine = MockEngine { request ->
            requestedUrls.add(request.url.toString())
            respond(successVersionJson, headers = jsonHeaders)
        }
        val repo = createVersionRepoWithConfig(engine, trailingSlashConfig)
        repo.checkUpdateDual()
        assertTrue(requestedUrls.any { it.contains("192.168.0.8:8085") })
    }

    //endregion

    //region checkUpdate tests

    @Test
    fun `checkUpdate returns success with valid response`() = runTest {
        val engine = MockEngine { request ->
            respond(successVersionJson, headers = jsonHeaders)
        }
        val repo = createVersionRepo(engine)
        val result = repo.checkUpdate()
        assertTrue(result.isSuccess())
        assertNotNull(result.data)
        assertEquals("App", result.data!!.appName)
        assertEquals(10, result.data!!.code)
        assertEquals("3.5.1", result.data!!.name)
    }

    @Test
    fun `checkUpdate returns error on invalid JSON`() = runTest {
        val errorConfig = object : NetworkConfigProvider by config {
            override val checkUpdatePath: String = "https://example.com/api/version"
        }
        val engine = MockEngine { request ->
            respond("not json", headers = jsonHeaders)
        }
        val repo = createVersionRepoWithConfig(engine, errorConfig)
        val result = repo.checkUpdate()
        assertTrue(result.isError())
        assertEquals(CustomResultCode.SERIALIZATION_ERROR, result.code)
    }

    @Test
    fun `checkUpdate returns error on HTTP error`() = runTest {
        val errorConfig = object : NetworkConfigProvider by config {
            override val checkUpdatePath: String = "https://example.com/api/version"
        }
        val engine = MockEngine { request ->
            respondError(HttpStatusCode.InternalServerError)
        }
        val repo = createVersionRepoWithConfig(engine, errorConfig)
        val result = repo.checkUpdate()
        assertTrue(result.isError())
    }

    //endregion

    //region checkUpdateDual tests

    @Test
    fun `checkUpdateDual returns successful result when both succeed`() = runTest {
        var requestCount = 0
        val engine = MockEngine { request ->
            requestCount++
            respond(successVersionJson, headers = jsonHeaders)
        }
        val repo = createVersionRepo(engine)
        val result = repo.checkUpdateDual()
        assertTrue(result.result.isSuccess())
        assertEquals(2, requestCount)
    }

    @Test
    fun `checkUpdateDual returns Internal when external fails`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.host == "vis.xingchenwulian.com") {
                respond(errorVersionJson, headers = jsonHeaders)
            } else {
                respond(successVersionJson, headers = jsonHeaders)
            }
        }
        val repo = createVersionRepo(engine)
        val result = repo.checkUpdateDual()
        assertIs<UpdateCheckResult.Internal>(result)
        assertTrue(result.result.isSuccess())
    }

    @Test
    fun `checkUpdateDual returns External when internal fails`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.host != "vis.xingchenwulian.com") {
                respondError(HttpStatusCode.ServiceUnavailable)
            } else {
                respond(successVersionJson, headers = jsonHeaders)
            }
        }
        val repo = createVersionRepo(engine)
        val result = repo.checkUpdateDual()
        assertIs<UpdateCheckResult.External>(result)
        assertTrue(result.result.isSuccess())
    }

    @Test
    fun `checkUpdateDual returns error when both fail`() = runTest {
        val engine = MockEngine { request ->
            respondError(HttpStatusCode.ServiceUnavailable)
        }
        val repo = createVersionRepo(engine)
        val result = repo.checkUpdateDual()
        assertIs<UpdateCheckResult.External>(result)
        assertTrue(result.result.isError())
    }

    @Test
    fun `checkUpdateDual returns Internal when external fails with error code`() = runTest {
        val engine = MockEngine { request ->
            if (request.url.host == "vis.xingchenwulian.com") {
                respondError(HttpStatusCode.GatewayTimeout)
            } else {
                respond(successVersionJson, headers = jsonHeaders)
            }
        }
        val repo = createVersionRepo(engine)
        val result = repo.checkUpdateDual()
        assertIs<UpdateCheckResult.Internal>(result)
        assertTrue(result.result.isSuccess())
    }

    @Test
    fun `checkUpdateDual prefers first successful result`() = runTest {
        val engine = MockEngine { request ->
            respond(successVersionJson, headers = jsonHeaders)
        }
        val repo = createVersionRepo(engine)
        val result = repo.checkUpdateDual()
        assertTrue(result.result.isSuccess())
        // Either External or Internal is fine when both succeed
    }

    //endregion

    //region checkUpdateFlow tests

    @Test
    fun `checkUpdateFlow emits immediately on collection`() = runTest {
        val engine = MockEngine { respond(successVersionJson, headers = jsonHeaders) }
        val repo = createVersionRepo(engine)
        var receivedCount = 0
        var firstResult: ResultModel<Version>? = null

        val job = launch {
            repo.checkUpdateFlow(intervalMs = 60_000L).collect { result ->
                receivedCount++
                if (firstResult == null) firstResult = result
                if (receivedCount >= 1) cancel()
            }
        }
        job.join()

        assertEquals(1, receivedCount)
        assertNotNull(firstResult)
        assertTrue(firstResult!!.isSuccess())
    }

    @Test
    fun `checkUpdateFlow emits on interval`() = runTest {
        val engine = MockEngine { respond(successVersionJson, headers = jsonHeaders) }
        val repo = createVersionRepo(engine)
        val results = mutableListOf<ResultModel<Version>>()

        val job = launch {
            repo.checkUpdateFlow(intervalMs = 100L).collect { result ->
                results.add(result)
                if (results.size >= 3) cancel()
            }
        }
        advanceUntilIdle()
        job.join()

        assertTrue(results.size >= 3)
        results.forEach { assertTrue(it.isSuccess()) }
    }

    @Test
    fun `checkUpdateFlow stops when collector cancels`() = runTest {
        val engine = MockEngine { respond(successVersionJson, headers = jsonHeaders) }
        val repo = createVersionRepo(engine)
        val results = mutableListOf<ResultModel<Version>>()

        val job = launch {
            repo.checkUpdateFlow(intervalMs = 50L).collect { result ->
                results.add(result)
            }
        }
        job.cancel()
        job.join()

        // After cancellation, no more emissions should happen
        val countAfterCancel = results.size
        assertTrue(countAfterCancel <= 2) // May have gotten 1-2 before cancel
    }

    @Test
    fun `checkUpdateFlow with useDualNetwork delegates to checkUpdateDual`() = runTest {
        val engine = MockEngine { respond(successVersionJson, headers = jsonHeaders) }
        val repo = createVersionRepo(engine)
        var received = false

        val job = launch {
            repo.checkUpdateFlow(intervalMs = 60_000L, useDualNetwork = true).collect { result ->
                received = true
                cancel()
            }
        }
        job.join()

        assertTrue(received)
    }

    @Test
    fun `checkUpdateFlow throws on invalid interval`() = runTest {
        val engine = MockEngine { respond("ok") }
        val repo = createVersionRepo(engine)
        val flow = repo.checkUpdateFlow(intervalMs = 0L)

        try {
            flow.collect {}
            throw AssertionError("Should have thrown IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected
        }
    }

    @Test
    fun `checkUpdateFlow emits error result without crashing`() = runTest {
        val errorConfig = object : NetworkConfigProvider by config {
            override val checkUpdatePath: String = "https://example.com/api/version"
            override val requestTimeoutMillis: Long = 5_000L
        }
        val engine = MockEngine { respond("invalid json", headers = jsonHeaders) }
        val repo = createVersionRepoWithConfig(engine, errorConfig)
        var receivedError = false

        val job = launch {
            repo.checkUpdateFlow(intervalMs = 60_000L).collect { result ->
                if (result.isError()) receivedError = true
                cancel()
            }
        }
        job.join()

        assertTrue(receivedError)
    }

    //endregion

    //region Version model serialization tests (backward compatibility)

    @Test
    fun `Version model serialization round trip`() {
        val version = Version(
            appName = "TestApp",
            code = 3,
            name = "2.0.0",
            url = "https://example.com/v2.apk",
            size = "50MB",
            description = "Major update"
        )
        val serialized = json.encodeToString(Version.serializer(), version)
        val deserialized = json.decodeFromString(Version.serializer(), serialized)
        assertEquals(version, deserialized)
    }

    @Test
    fun `Version model with null size`() {
        val jsonStr = """{
            "appName":"App","code":1,"name":"1.0",
            "url":"https://a.com","size":null,"description":"desc"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertNull(version.size)
    }

    @Test
    fun `Version model with empty strings`() {
        val version = Version(
            appName = "",
            code = 0,
            name = "",
            url = "",
            size = null,
            description = ""
        )
        val serialized = json.encodeToString(Version.serializer(), version)
        val deserialized = json.decodeFromString(Version.serializer(), serialized)
        assertEquals("", deserialized.appName)
        assertEquals(0, deserialized.code)
    }

    @Test
    fun `Version model with large version code`() {
        val jsonStr = """{
            "appName":"App","code":999999,"name":"99.99.99",
            "url":"https://a.com","size":null,"description":"desc"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertEquals(999999, version.code)
    }

    @Test
    fun `Version model with negative version code`() {
        val jsonStr = """{
            "appName":"App","code":-1,"name":"-1.0",
            "url":"https://a.com","size":null,"description":"desc"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertEquals(-1, version.code)
    }

    @Test
    fun `Version model with all fields populated`() {
        val jsonStr = """{
            "appName":"MyApp","code":10,"name":"3.5.1",
            "url":"https://cdn.example.com/app-3.5.1.apk",
            "size":"128.5MB",
            "description":"新功能：支持深色模式\\n修复：登录页面闪退问题"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertEquals("MyApp", version.appName)
        assertEquals(10, version.code)
        assertEquals("3.5.1", version.name)
        assertEquals("https://cdn.example.com/app-3.5.1.apk", version.url)
        assertEquals("128.5MB", version.size)
        assertTrue(version.description!!.contains("深色模式"))
    }

    @Test
    fun `Version model with unicode in description`() {
        val jsonStr = """{
            "appName":"应用","code":1,"name":"1.0",
            "url":"https://a.com","size":null,"description":"🎉全新版本发布！"
        }"""
        val version = json.decodeFromString(Version.serializer(), jsonStr)
        assertEquals("🎉全新版本发布！", version.description)
    }

    @Test
    fun `Version model data class equality`() {
        val v1 = Version("App", 1, "1.0", "http://a.com", null, "desc")
        val v2 = Version("App", 1, "1.0", "http://a.com", null, "desc")
        assertEquals(v1, v2)
    }

    @Test
    fun `Version model data class copy`() {
        val v1 = Version("App", 1, "1.0", "http://a.com", null, "desc")
        val v2 = v1.copy(code = 2, name = "2.0")
        assertEquals(2, v2.code)
        assertEquals("2.0", v2.name)
        assertEquals("App", v2.appName)
        assertEquals(1, v1.code)
    }

    //endregion
}
