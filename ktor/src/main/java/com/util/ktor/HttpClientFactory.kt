package com.util.ktor

import android.util.Log
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.data.login.model.UserToken
import com.util.ktor.model.ResultModel
import com.util.ktor.plugin.AuthRefreshLock
import com.util.ktor.plugin.CustomAuthTriggerPlugin
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngineConfig
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.InternalAPI
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

private const val TAG = "HttpClientFactory"

fun createDefaultHttpClient(
    config: NetworkConfigProvider,
    json: Json,
    authRefreshLock: AuthRefreshLock = AuthRefreshLock(),
): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            }
        }
        installPlugins(config, json, authRefreshLock)
    }
}

fun createNoAuthDefaultHttpClient(
    json: Json,
    logLevel: LogLevel = LogLevel.NONE,
): HttpClient {
    return HttpClient(OkHttp) {
        engine {
            config {
                protocols(listOf(okhttp3.Protocol.HTTP_1_1))
            }
        }
        installCommonPlugins(json, logLevel)
    }
}

internal fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installCommonPlugins(
    json: Json,
    logLevel: LogLevel = LogLevel.NONE,
) {
    install(ContentNegotiation) {
        json(json)
    }
    if (logLevel != LogLevel.NONE) {
        install(Logging) {
            logger = Logger.ANDROID
            level = logLevel
        }
    }
}

/**
 * Installs all plugins including auth, timeout, logging, and default request configuration.
 *
 * Note: Uses Ktor [InternalAPI] for token refresh pipeline. This is required because
 * Ktor's stable API does not yet expose a public mechanism for intercepting and rewriting
 * response status codes in the receive pipeline.
 */
@OptIn(InternalAPI::class)
fun <T : HttpClientEngineConfig> HttpClientConfig<T>.installPlugins(
    config: NetworkConfigProvider,
    json: Json,
    authRefreshLock: AuthRefreshLock = AuthRefreshLock(),
) {
    val logLevel = if (config.isLogEnabled) LogLevel.ALL else LogLevel.NONE
    installCommonPlugins(json, logLevel)

    install(CustomAuthTriggerPlugin) {
        this.json = json
    }

    install(HttpTimeout) {
        requestTimeoutMillis = config.requestTimeoutMillis
        connectTimeoutMillis = config.connectTimeoutMillis
        socketTimeoutMillis = config.socketTimeoutMillis
    }

    install(Auth) {
        bearer {
            realm = "Access to protected resources"

            loadTokens {
                val token = config.token
                if (token.isNotEmpty()) {
                    if (config.isLogEnabled) Log.d(TAG, "loadTokens: 使用现有 token")
                    BearerTokens(accessToken = token, refreshToken = null)
                } else {
                    if (config.isLogEnabled) Log.d(TAG, "loadTokens: 无已保存的 token")
                    null
                }
            }

            sendWithoutRequest { request ->
                val isLoginPath = request.url.encodedPath == config.loginPath
                val hasToken = config.token.isNotEmpty()
                !isLoginPath && hasToken
            }

            refreshTokens {
                authRefreshLock.withLock {
                    try {
                        val oldTokens = this.oldTokens
                        val currentSavedToken = config.token

                        if (config.isLogEnabled) {
                            val oldTokenPreview = oldTokens?.accessToken?.let {
                                if (it.length > 16) "${it.take(6)}...${it.takeLast(6)}" else it
                            } ?: "null"
                            val savedTokenPreview = if (currentSavedToken.length > 16)
                                "${currentSavedToken.take(6)}...${currentSavedToken.takeLast(6)}"
                            else
                                currentSavedToken.ifEmpty { "empty" }
                            Log.d(
                                TAG,
                                "refreshTokens - oldToken: $oldTokenPreview, savedToken: $savedTokenPreview, 相同: ${oldTokens?.accessToken == currentSavedToken}"
                            )
                        }

                        if (currentSavedToken.isNotEmpty() && oldTokens?.accessToken != currentSavedToken) {
                            if (config.isLogEnabled) Log.d(TAG, "Token 已被更新，直接使用新 Token")
                            BearerTokens(
                                accessToken = currentSavedToken,
                                refreshToken = null
                            )
                        } else {
                            if (config.isLogEnabled) Log.d(TAG, "Token 已过期，开始执行刷新操作...")
                            val loginPayload = createLoginModel(
                                json,
                                config.getLoginKeyStyle(),
                                config.username,
                                config.password
                            )

                            val response = client.post(config.loginPath) {
                                markAsRefreshTokenRequest()
                                setBody(loginPayload)
                                contentType(ContentType.Application.Json)
                            }

                            val rawResult = response.body<ResultModel<JsonElement>>()
                            if (rawResult.isSuccess()) {
                                val userToken = when (val dataElement = rawResult.data) {
                                    is JsonPrimitive -> {
                                        if (dataElement.isString) UserToken(token = dataElement.content) else null
                                    }
                                    is JsonObject -> json.decodeFromJsonElement<UserToken>(dataElement)
                                    else -> null
                                }
                                if (userToken != null) {
                                    config.onNewTokenReceived(userToken.token, userToken.tenant)
                                    if (config.isLogEnabled) Log.d(TAG, "Token 刷新成功")
                                    BearerTokens(accessToken = userToken.token, refreshToken = null)
                                } else {
                                    Log.e(TAG, "重新登录失败: 无法解析 token")
                                    config.onTokenRefreshFailed(ResultModel(code = rawResult.code, message = rawResult.message))
                                    null
                                }
                            } else {
                                Log.e(TAG, "重新登录失败: code=${rawResult.code}, msg=${rawResult.message}")
                                config.onTokenRefreshFailed(ResultModel(code = rawResult.code, message = rawResult.message))
                                null
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Token 刷新异常", e)
                        null
                    }
                }
            }
        }
    }

    install(DefaultRequest) {
        val uri = java.net.URI(config.serverAddress)
        val host = uri.host
            ?: config.serverAddress.removePrefix("https://").removePrefix("http://")
        val protocol = when (uri.scheme) {
            "https" -> URLProtocol.HTTPS
            "http" -> URLProtocol.HTTP
            else -> if (config.serverAddress.startsWith("https://")) URLProtocol.HTTPS else URLProtocol.HTTP
        }
        val port = config.serverPort.toIntOrNull() ?: uri.port.takeIf { it != -1 }

        url {
            this.protocol = protocol
            this.host = host
            port?.let { this.port = it }
            contentType(ContentType.Application.Json)
        }
        val tenant = config.tenant
        if (tenant.isNotEmpty()) {
            header("tenant", tenant)
        }
    }
}
