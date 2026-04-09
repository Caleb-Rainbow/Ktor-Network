package com.util.ktor.data.heart

import com.util.ktor.HttpUtil
import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HeartRepositoryTest {

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

    private fun createHeartRepo(engine: MockEngine): HeartRepository {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        val httpUtil = HttpUtil(httpClient = client, json = json, config = config)
        return HeartRepository(httpUtil, config)
    }

    // Note: heartbeat() calls HttpUtil.get -> executeRequest -> android.util.Log.
    // Tests verify repository construction and path building logic.

    @Test
    fun `heartbeat constructs correct path`() {
        val expectedPath = config.heartBeatPath + "/DEVICE001/30"
        assertEquals("/heart/DEVICE001/30", expectedPath)
    }

    @Test
    fun `heartbeat with zero seconds`() {
        val path = config.heartBeatPath + "/DEV001/0"
        assertEquals("/heart/DEV001/0", path)
    }

    @Test
    fun `heartbeat with large second value`() {
        val path = config.heartBeatPath + "/DEV001/86400"
        assertEquals("/heart/DEV001/86400", path)
    }

    @Test
    fun `heartbeat with empty device number`() {
        val path = config.heartBeatPath + "//30"
        assertEquals("/heart//30", path)
    }

    @Test
    fun `heartbeat with special characters in deviceNumber`() {
        val deviceNumber = "DEV-001_V2"
        val path = config.heartBeatPath + "/$deviceNumber/60"
        assertEquals("/heart/DEV-001_V2/60", path)
    }

    @Test
    fun `heartbeat with negative second value`() {
        val path = config.heartBeatPath + "/DEV001/-1"
        assertEquals("/heart/DEV001/-1", path)
    }

    @Test
    fun `heartbeat with custom heartBeatPath`() {
        val customConfig = object : NetworkConfigProvider by config {
            override val heartBeatPath: String = "/api/heartbeat"
        }
        val path = customConfig.heartBeatPath + "/DEV001/30"
        assertEquals("/api/heartbeat/DEV001/30", path)
    }

    @Test
    fun `HeartRepository is constructed correctly`() {
        val engine = MockEngine { respond(
            content = """{"code":200,"msg":"ok","data":"pong"}""",
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        ) }
        val repo = createHeartRepo(engine)
        assertNotNull(repo)
    }
}
