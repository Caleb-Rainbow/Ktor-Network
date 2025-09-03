package com.util.ktor

import android.util.Log
import com.util.ktor.config.NetworkConfig
import com.util.ktor.model.LoginModel
import com.util.ktor.model.ResultCodeType
import com.util.ktor.model.ResultModel
import com.util.ktor.model.UserToken
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.serializer
import java.io.File

/**
 * @description http请求封装
 * @author 杨帅林
 * @create 2024/10/18 14:09
 **/
class HttpUtil(
    val httpClient: HttpClient,
    val json: Json,
    val config: NetworkConfig
) {
    suspend fun <T> makeRequest(
        serializer: KSerializer<ResultModel<T>>,
        method: HttpMethod,
        path: String,
        body: Any? = null,
        contentType: ContentType = ContentType.Application.Json,
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap()
    ): ResultModel<T> {
        try {
            val localHost = config.serverAddress
            val localPort = config.serverPort.toIntOrNull()
            val token = config.token
            val tenant = config.tenant
            val response = when (method) {
                HttpMethod.Post -> httpClient.post {
                    setupRequest(
                        token,
                        localHost,
                        localPort,
                        tenant,
                        contentType,
                        this,
                        path,
                        headersMap,
                        parametersMap,
                        body
                    )
                }.bodyAsText()

                HttpMethod.Get -> httpClient.get {
                    setupRequest(
                        token,
                        localHost,
                        localPort,
                        tenant,
                        contentType,
                        this,
                        path,
                        headersMap,
                        parametersMap
                    )
                }.bodyAsText()

                HttpMethod.Delete -> httpClient.delete {
                    setupRequest(
                        token,
                        localHost,
                        localPort,
                        tenant,
                        contentType,
                        this,
                        path,
                        headersMap,
                        parametersMap
                    )
                }.bodyAsText()

                HttpMethod.Put -> httpClient.put {
                    setupRequest(
                        token,
                        localHost,
                        localPort,
                        tenant,
                        contentType,
                        this,
                        path,
                        headersMap,
                        parametersMap,
                        body
                    )
                }.bodyAsText()

                else -> ""
            }
            Log.d(method.value, "path: $path  response: $response")
            val result = json.decodeFromString(serializer, response)
            if (result.code == ResultCodeType.NO_LOGIN.code && path != config.loginPath) {
                val r = httpClient.post {
                    url {
                        this.protocol = if (localHost.startsWith("https")) URLProtocol.HTTPS else URLProtocol.HTTP
                        this.host = localHost.removePrefix("https://").removePrefix("http://")
                        localPort?.let {
                            this.port = it
                        }
                        path(config.loginPath)
                        setBody(
                            json.encodeToString(LoginModel(config.username, config.password))
                        )
                    }
                    contentType(ContentType.Application.Json)
                }.bodyAsText()
                Log.d("Post", "makeRequest: 重新登录  path: ${config.loginPath}  response: $r")
                val loginResult = json.decodeFromString<ResultModel<UserToken>>(r)
                if (loginResult.isSuccess()) {
                    loginResult.token?.let {
                        config.onNewTokenReceived(it.token,it.tenant)
                    }
                    return makeRequest(serializer, method, path, body, contentType, headersMap, parametersMap)
                } else {
                    return ResultModel.Companion.error(message = loginResult.message ?: "登录失败")
                }
            } else {
                return result
            }

        } catch (e: Exception) {
            return handleException(e, path)
        }
    }

    private fun setupRequest(
        token: String,
        host: String,
        port: Int?,
        tenant: String,
        contentType: ContentType,
        requestBuilder: HttpRequestBuilder,
        path: String,
        headersMap: Map<String, String>,
        parametersMap: Map<String, String>,
        body: Any? = null
    ) {
        // 设置请求头
        headersMap.forEach {
            requestBuilder.headers.append(it.key, it.value)
        }

        if (token.isNotEmpty()) {
            requestBuilder.headers.append(HttpHeaders.Authorization, "Bearer $token")
        }
        if (tenant.isNotEmpty()) {
            requestBuilder.headers.append("tenant", tenant)
        }
        if (!path.contains("http")) {
            // 设置 URL 和参数
            requestBuilder.url {
                this.protocol = if (host.startsWith("https")) URLProtocol.HTTPS else URLProtocol.HTTP
                this.host = host.split("//")[1]
                port?.let {
                    this.port = it
                }
                path(path)
                parametersMap.forEach {
                    parameters.append(it.key, it.value)
                }
                // 设置请求体
                body?.let {
                    requestBuilder.setBody(body)
                }
            }
        } else {
            requestBuilder.url(path)
            requestBuilder.url {
                parametersMap.forEach {
                    parameters.append(it.key, it.value)
                }
                // 设置请求体
                body?.let {
                    requestBuilder.setBody(body)
                }
            }
        }
        requestBuilder.contentType(contentType)
    }

    private fun <T> handleException(e: Exception, path: String): ResultModel<T> {
        Log.e("http", "Error in path: $path \n" + e.stackTraceToString())
        return when (e) {
            is HttpRequestTimeoutException -> ResultModel(ResultCodeType.ERROR.code, "网络请求超时")
            is ConnectTimeoutException -> ResultModel(ResultCodeType.ERROR.code, "网络连接超时")
            is SerializationException -> ResultModel(ResultCodeType.ERROR.code, "数据解析异常")
            else -> ResultModel(ResultCodeType.ERROR.code, "网络连接异常")
        }
    }

    // POST 请求
    suspend inline fun <reified T> post(
        path: String,
        body: Any? = null,
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap()
    ): ResultModel<T> {
        return withContext(Dispatchers.IO) {
            makeRequest(
                serializer = serializer(),
                HttpMethod.Post,
                path,
                body,
                headersMap = headersMap,
                parametersMap = parametersMap
            )
        }
    }

    // GET 请求
    suspend inline fun <reified T> get(
        path: String,
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap()
    ): ResultModel<T> {
        return withContext(Dispatchers.IO) {
            makeRequest(
                serializer = serializer(),
                HttpMethod.Get,
                path,
                headersMap = headersMap,
                parametersMap = parametersMap
            )
        }
    }

    // DELETE 请求
    suspend inline fun <reified T> delete(
        path: String,
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap()
    ): ResultModel<T> {
        return withContext(Dispatchers.IO) {
            makeRequest(
                serializer = serializer(),
                HttpMethod.Delete,
                path,
                headersMap = headersMap,
                parametersMap = parametersMap
            )
        }
    }

    // PUT 请求
    suspend inline fun <reified T> put(
        path: String,
        body: String = "",
        headersMap: Map<String, String> = emptyMap(),
        parametersMap: Map<String, String> = emptyMap()
    ): ResultModel<T> {
        return withContext(Dispatchers.IO) {
            makeRequest(
                serializer = serializer(),
                HttpMethod.Put,
                path,
                body,
                headersMap = headersMap,
                parametersMap = parametersMap
            )
        }
    }


    suspend fun uploadFile(file: File,config: NetworkConfig): ResultModel<JsonObject> {
        return withContext(Dispatchers.IO) {
            val url = kotlin.text.StringBuilder().append(config.serverAddress)
            if (config.serverPort.isNotEmpty()) url.append(":").append(config.serverPort)
            url.append(config.uploadFilePath)
            //存储桶名称
            url.append("?bucketName=scaleErp")
            val result = httpClient.submitFormWithBinaryData(url = url.toString(), formData = formData {
                append("file", file.readBytes(), headers = Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=${file.name}")
                })
            }) {
                if (config.token.isNotEmpty()) {
                    headers.append(HttpHeaders.Authorization, "Bearer ${config.token}")
                }
                if (config.tenant.isNotEmpty()) {
                    headers.append("tenant", config.tenant)
                }
                headers.append(HttpHeaders.Connection, "close")
            }.bodyAsText()
            Log.d("POST", "path: ${config.uploadFilePath}  response: $result")
            json.decodeFromString(result)
        }
    }
}

fun createDefaultHttpClient(): HttpClient {
    return HttpClient(CIO) {
        install(HttpTimeout) {
            requestTimeoutMillis = 30000
            connectTimeoutMillis = 30000
            socketTimeoutMillis = 30000
        }
    }
}