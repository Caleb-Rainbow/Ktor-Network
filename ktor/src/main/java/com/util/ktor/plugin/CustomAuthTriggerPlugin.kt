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

/**
 * 用于同步 Token 刷新操作的全局互斥锁。
 * 确保在任何时候只有一个协程可以执行刷新 Token 的网络请求。
 */
object AuthRefreshLock {
    val mutex = Mutex()
}
/**
 * 自定义 Ktor 插件，用于在 Auth 插件之前触发 Token 刷新。
 * 它会检查响应体中的业务错误码 (例如 code=401)，
 * 如果匹配，则将响应的 HTTP 状态码修改为 401 Unauthorized，
 * 以便让内置的 Auth 插件能够捕获到并执行刷新逻辑。
 */
class CustomAuthTriggerPlugin(private val config: Config) {

    class Config {
        // 用于解析响应体的 Json 实例
        lateinit var json: Json
        // 定义哪个业务 code 代表 Token 失效
        var tokenExpiredCode: Int = ResultCodeType.NO_LOGIN.code
    }

    companion object Plugin : HttpClientPlugin<Config, CustomAuthTriggerPlugin> {
        override val key: AttributeKey<CustomAuthTriggerPlugin> = AttributeKey("CustomAuthTriggerPlugin")

        override fun prepare(block: Config.() -> Unit): CustomAuthTriggerPlugin {
            val config = Config().apply(block)
            return CustomAuthTriggerPlugin(config)
        }

        @InternalAPI
        override fun install(plugin: CustomAuthTriggerPlugin, scope: HttpClient) {
            // 监听响应事件
            scope.receivePipeline.intercept(HttpReceivePipeline.After) { response ->
                // 确保响应成功 (status 200) 且是 JSON 类型
                if (response.status == HttpStatusCode.OK &&
                    response.contentType()?.match(ContentType.Application.Json) == true) {

                    // 读取响应体文本
                    val originalBody = response.bodyAsText()

                    try {
                        // 解析 JSON 并检查业务 code
                        val jsonObject = plugin.config.json.parseToJsonElement(originalBody).jsonObject
                        val code = jsonObject["code"]?.jsonPrimitive?.content?.toIntOrNull()
                        if (code == plugin.config.tokenExpiredCode) {
                            // 记录日志，便于调试
                            val requestUrl = response.call.request.url.encodedPath
                            println("CustomAuthTrigger: 检测到 code=$code (token过期)，请求路径: $requestUrl，将状态码改为 401")
                            
                            // 如果 code 匹配，创建一个新的、状态码为 401 的响应
                            val newResponse = object : HttpResponse() {
                                override val call: HttpClientCall = response.call
                                override val status: HttpStatusCode = HttpStatusCode.Unauthorized // 关键：修改状态码
                                override val version: HttpProtocolVersion = response.version
                                override val requestTime: GMTDate = response.requestTime
                                override val responseTime: GMTDate = response.responseTime
                                override val headers: Headers = response.headers
                                // 将原始 body 作为新响应的内容
                                override val rawContent: ByteReadChannel = ByteReadChannel(originalBody)
                                override val coroutineContext: CoroutineContext = response.coroutineContext
                            }
                            // 用新响应替换旧响应，继续处理流程
                            proceedWith(newResponse)
                            return@intercept
                        }
                    } catch (e: Exception) {
                        Log.e("TAG", "install: ", e)
                        // 解析失败或字段不存在，忽略并继续
                    }
                    // 如果 code 不匹配，需要重新构建一个包含原始 body 的响应，因为 bodyAsText() 只能消费一次
                    val newResponseWithOriginalBody = object : HttpResponse() {
                        override val call: HttpClientCall = response.call
                        override val status: HttpStatusCode = response.status
                        override val version: HttpProtocolVersion = response.version
                        override val requestTime: GMTDate = response.requestTime
                        override val responseTime: GMTDate = response.responseTime
                        override val headers: Headers = response.headers
                        override val rawContent: ByteReadChannel = ByteReadChannel(originalBody)
                        override val coroutineContext: CoroutineContext = response.call.coroutineContext
                    }
                    proceedWith(newResponseWithOriginalBody)
                } else {
                    // 如果不是 200 OK 或不是 JSON，直接继续
                    proceed()
                }
            }
        }
    }
}