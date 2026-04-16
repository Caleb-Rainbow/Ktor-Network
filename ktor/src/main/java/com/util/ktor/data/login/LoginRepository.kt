package com.util.ktor.data.login

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.createLoginModel
import com.util.ktor.data.login.model.UserToken
import com.util.ktor.model.ResultModel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

class LoginRepository(
    private val httpUtil: HttpUtil,
    private val json: Json,
    private val config: NetworkConfigProvider
) {

    /**
     * 兼容新旧两种登录协议：
     * - 新协议：data 是对象 { token, officeId, tenant, ... }
     * - 旧协议：data 是纯字符串 token
     * 一次请求，根据 data 的实际类型自动适配。
     */
    suspend fun passwordLogin(
        host: String = "",
        username: String,
        password: String,
    ): ResultModel<UserToken> {
        val rawResult = httpUtil.post<JsonElement>(
            path = host + config.loginPath,
            body = createLoginModel(
                json = json,
                style = config.getLoginKeyStyle(),
                username = username,
                password = password
            )
        )

        if (rawResult.isError()) {
            return ResultModel(code = rawResult.code, message = rawResult.message)
        }

        val userToken = when (val dataElement = rawResult.data) {
            is JsonPrimitive -> {
                if (dataElement.isString) UserToken(token = dataElement.content) else null
            }
            is JsonObject -> json.decodeFromJsonElement<UserToken>(dataElement)
            else -> null
        }

        return ResultModel(
            code = rawResult.code,
            message = rawResult.message,
            data = userToken
        )
    }

    suspend fun passwordLoginNew(
        host: String = "",
        username: String,
        password: String,
    ): ResultModel<UserToken> {
        return httpUtil.post<UserToken>(
            path = host + config.loginPath,
            body = createLoginModel(
                json = json,
                style = config.getLoginKeyStyle(),
                username = username,
                password = password
            )
        )
    }

    suspend fun passwordLoginOld(
        host: String = "",
        username: String,
        password: String,
    ): ResultModel<String> {
        return httpUtil.post(
            path = host + config.loginPath,
            body = createLoginModel(
                json = json,
                style = config.getLoginKeyStyle(),
                username = username,
                password = password
            )
        )
    }
}
