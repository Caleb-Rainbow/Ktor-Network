package com.util.ktor

import com.util.ktor.config.LoginKeyStyle
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

fun createLoginModel(
    json: Json,
    style: LoginKeyStyle,
    username: String,
    password: String,
): String {
    return json.encodeToString(JsonObject.serializer(), buildJsonObject {
        when (style) {
            LoginKeyStyle.CAMEL_CASE_V1 -> {
                put("userName", username)
                put("passWord", password)
            }

            LoginKeyStyle.LOWER_CASE_V2 -> {
                put("username", username)
                put("password", password)
            }
        }
    })
}
