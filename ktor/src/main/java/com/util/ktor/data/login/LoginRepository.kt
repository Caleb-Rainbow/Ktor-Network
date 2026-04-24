package com.util.ktor.data.login

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.createLoginModel
import com.util.ktor.data.login.model.UserToken
import com.util.ktor.model.CustomResultCode
import com.util.ktor.model.ResultCodeType
import com.util.ktor.model.ResultModel
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.path
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

class LoginRepository(
    private val httpUtil: HttpUtil,
    private val json: Json,
    private val config: NetworkConfigProvider
) {

    /**
     * 兼容多种登录响应格式，统一返回 ResultModel<UserToken>：
     * - 格式A：{ code, msg, data: { token, officeId, tenant, ... } }
     * - 格式B：{ code, msg, data: "eyJhbG..." }
     * - 格式C：{ code, msg, token: { token, deptId, ... } }
     * - 格式D：{ code, msg, token: "eyJhbG...", deptId, tenant, ... }
     * 请求格式通过 NetworkConfigProvider.getLoginKeyStyle() 自动适配
     */
    suspend fun passwordLogin(
        host: String = "",
        username: String,
        password: String,
    ): ResultModel<UserToken> {
        val path = host + config.loginPath

        val rawBody: String = try {
            httpUtil.executeRawRequest {
                method = HttpMethod.Post
                if (path.startsWith("http://") || path.startsWith("https://")) {
                    url(path)
                } else {
                    url { path(path) }
                }
                contentType(ContentType.Application.Json)
                setBody(
                    createLoginModel(
                        json = json,
                        style = config.getLoginKeyStyle(),
                        username = username,
                        password = password
                    )
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return httpUtil.handleException(e)
        }

        return parseLoginResponse(rawBody)
    }

    private fun parseLoginResponse(rawBody: String): ResultModel<UserToken> {
        return try {
            val responseObj = json.decodeFromString<JsonObject>(rawBody)
            val code = responseObj["code"]?.jsonPrimitive?.intOrNull
            val message = responseObj["msg"]?.jsonPrimitive?.content

            if (code != ResultCodeType.OK.code) {
                return ResultModel(code = code ?: CustomResultCode.SERIALIZATION_ERROR, message = message)
            }

            ResultModel(
                code = code,
                message = message,
                data = parseLoginUserToken(json, responseObj)
            )
        } catch (e: SerializationException) {
            ResultModel(code = CustomResultCode.SERIALIZATION_ERROR, message = "数据解析异常: ${e.message}")
        }
    }
}
