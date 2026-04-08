package com.util.ktor.data.login

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.createLoginModel
import com.util.ktor.data.login.model.UserToken
import com.util.ktor.model.ResultModel
import kotlinx.serialization.json.Json

class LoginRepository(
    private val httpUtil: HttpUtil,
    private val json: Json,
    private val config: NetworkConfigProvider
) {
    suspend fun passwordLogin(
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
}
