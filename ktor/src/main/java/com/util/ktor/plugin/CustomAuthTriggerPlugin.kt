package com.util.ktor.plugin

import android.util.Log
import com.util.ktor.model.ResultCodeType
import io.ktor.client.HttpClient
import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.HttpClientPlugin
import io.ktor.client.statement.HttpReceivePipeline
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpProtocolVersion
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext

class AuthRefreshLock {
    private val _mutex = Mutex()

    /**
     * 获取互斥锁并执行给定的挂起函数。
     * 仅暴露必要的 `withLock` 操作，防止外部直接操作互斥锁。
     */
    suspend fun <T> withLock(block: suspend () -> T): T = _mutex.withLock { block() }
}

class CustomAuthTriggerPlugin(private val config: Config) {

    class Config {
        var json: Json = Json { ignoreUnknownKeys = true }
        var tokenExpiredCode: Int = ResultCodeType.NO_LOGIN.code

        /**
         * 触发 token 过期检测的最大响应体大小（字节）。
         * 超过此大小的响应将被跳过检测，直接传递。
         * 默认 64KB，防止大响应体被完整读入内存导致 OOM。
         */
        var maxBodySizeForCheck: Long = 64L * 1024L
    }

    companion object Plugin : HttpClientPlugin<Config, CustomAuthTriggerPlugin> {
        private const val TAG = "CustomAuthTrigger"

        override val key: AttributeKey<CustomAuthTriggerPlugin> =
            AttributeKey("CustomAuthTriggerPlugin")

        override fun prepare(block: Config.() -> Unit): CustomAuthTriggerPlugin {
            val config = Config().apply(block)
            return CustomAuthTriggerPlugin(config)
        }

        @InternalAPI
        override fun install(plugin: CustomAuthTriggerPlugin, scope: HttpClient) {
            scope.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
                if (response.status == HttpStatusCode.OK &&
                    response.contentType()?.match(ContentType.Application.Json) == true &&
                    (response.contentLength() ?: 0L) <= plugin.config.maxBodySizeForCheck
                ) {
                    val originalBody = try {
                        response.bodyAsText()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "无法读取响应体，跳过 token 过期检测", e)
                        proceed()
                        return@intercept
                    }
                    try {
                        val jsonObject =
                            plugin.config.json.parseToJsonElement(originalBody).jsonObject
                        val code =
                            jsonObject["code"]?.jsonPrimitive?.content?.toIntOrNull()
                        if (code == plugin.config.tokenExpiredCode) {
                            val requestUrl = response.call.request.url.encodedPath
                            Log.d(
                                TAG,
                                "检测到 code=$code (token过期)，请求路径: $requestUrl，将状态码改为 401"
                            )
                            proceedWith(
                                wrapResponse(
                                    response,
                                    HttpStatusCode.Unauthorized,
                                    originalBody
                                )
                            )
                            return@intercept
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "解析响应体失败", e)
                    }
                    proceedWith(wrapResponse(response, response.status, originalBody))
                } else {
                    proceed()
                }
            }
        }
    }
}

@InternalAPI
private fun wrapResponse(
    original: HttpResponse,
    status: HttpStatusCode,
    body: String,
): HttpResponse = object : HttpResponse() {
    override val call: HttpClientCall = original.call
    override val status: HttpStatusCode = status
    override val version: HttpProtocolVersion = original.version
    override val requestTime: GMTDate = original.requestTime
    override val responseTime: GMTDate = original.responseTime
    override val headers: Headers = original.headers
    override val rawContent: ByteReadChannel = ByteReadChannel(body)
    override val coroutineContext: CoroutineContext = original.coroutineContext
}
