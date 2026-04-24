package com.util.ktor.data.login

import com.util.ktor.data.login.model.UserToken
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * 从登录响应 JSON 中提取 UserToken。
 *
 * 支持四种格式：
 * - A: { code, msg, data: { token, officeId, tenant, ... } }
 * - B: { code, msg, data: "eyJhbG..." }
 * - C: { code, msg, token: { token, deptId, ... } }
 * - D: { code, msg, token: "eyJhbG...", deptId, tenant, ... }
 *
 * 优先级：先检查顶层 `token` 字段，再检查 `data` 字段。
 */
fun parseLoginUserToken(json: Json, response: JsonObject): UserToken? {
    val tokenElement = response["token"]
    if (tokenElement != null) {
        val token = tokenElement.toUserToken(json)
        if (token != null) return token
    }
    val dataElement = response["data"]
    return dataElement?.toUserToken(json)
}

private fun JsonElement.toUserToken(json: Json): UserToken? = when (this) {
    is JsonPrimitive -> {
        if (isString) UserToken(token = content) else null
    }
    is JsonObject -> json.decodeFromJsonElement<UserToken>(this)
    else -> null
}
