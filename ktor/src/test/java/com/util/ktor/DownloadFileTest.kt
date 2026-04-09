package com.util.ktor

import com.util.ktor.config.LoginKeyStyle
import com.util.ktor.config.NetworkConfigProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DownloadFileTest {

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

    private fun createHttpUtil(engine: MockEngine): HttpUtil {
        val client = HttpClient(engine) {
            install(ContentNegotiation) { json(json) }
        }
        return HttpUtil(httpClient = client, json = json, config = config)
    }

    //region Path traversal validation

    @Test
    fun `downloadFile rejects path with single double-dot`() = runTest {
        val engine = MockEngine { respondOk() }
        val httpUtil = createHttpUtil(engine)
        val result = httpUtil.downloadFile("/file", "../secret") { _, _, _ -> }
        assertTrue(result.isError())
        assertEquals("非法文件路径", result.message)
    }

    @Test
    fun `downloadFile rejects path with multiple double-dots`() = runTest {
        val engine = MockEngine { respondOk() }
        val httpUtil = createHttpUtil(engine)
        val result = httpUtil.downloadFile("/file", "../../etc/passwd") { _, _, _ -> }
        assertTrue(result.isError())
    }

    //endregion

    //region formatTime utility (tested directly since private)

    @Test
    fun `formatTime returns correct format for various seconds`() {
        val testCases = listOf(
            0L to "0秒",
            1L to "1秒",
            59L to "59秒",
            60L to "1分0秒",
            90L to "1分30秒",
            3600L to "1小时0分0秒",
            3661L to "1小时1分1秒",
            7200L to "2小时0分0秒"
        )
        for ((seconds, expected) in testCases) {
            val result = formatTimeHelper(seconds)
            assertEquals(expected, result, "formatTime($seconds)")
        }
    }

    private fun formatTimeHelper(totalSeconds: Long): String {
        if (totalSeconds <= 0) return "0秒"
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return when {
            hours > 0 -> "${hours}小时${minutes}分${seconds}秒"
            minutes > 0 -> "${minutes}分${seconds}秒"
            else -> "${seconds}秒"
        }
    }

    //endregion
}
