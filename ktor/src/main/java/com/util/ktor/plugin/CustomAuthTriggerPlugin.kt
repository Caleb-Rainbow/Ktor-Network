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
import io.ktor.http.contentType
import io.ktor.util.AttributeKey
import io.ktor.util.date.GMTDate
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.coroutines.CoroutineContext

class AuthRefreshLock {
    val mutex = Mutex()
}

class CustomAuthTriggerPlugin(private val config: Config) {

    class Config {
        lateinit var json: Json
        var tokenExpiredCode: Int = ResultCodeType.NO_LOGIN.code
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
                    response.contentType()?.match(ContentType.Application.Json) == true
                ) {
                    val originalBody = response.bodyAsText()
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
                            val newResponse = object : HttpResponse() {
                                override val call: HttpClientCall = response.call
                                override val status: HttpStatusCode =
                                    HttpStatusCode.Unauthorized
                                override val version: HttpProtocolVersion = response.version
                                override val requestTime: GMTDate = response.requestTime
                                override val responseTime: GMTDate = response.responseTime
                                override val headers: Headers = response.headers
                                override val rawContent: ByteReadChannel =
                                    ByteReadChannel(originalBody)
                                override val coroutineContext: CoroutineContext =
                                    response.coroutineContext
                            }
                            proceedWith(newResponse)
                            return@intercept
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "解析响应体失败", e)
                    }
                    val newResponseWithOriginalBody = object : HttpResponse() {
                        override val call: HttpClientCall = response.call
                        override val status: HttpStatusCode = response.status
                        override val version: HttpProtocolVersion = response.version
                        override val requestTime: GMTDate = response.requestTime
                        override val responseTime: GMTDate = response.responseTime
                        override val headers: Headers = response.headers
                        override val rawContent: ByteReadChannel =
                            ByteReadChannel(originalBody)
                        override val coroutineContext: CoroutineContext =
                            response.call.coroutineContext
                    }
                    proceedWith(newResponseWithOriginalBody)
                } else {
                    proceed()
                }
            }
        }
    }
}
