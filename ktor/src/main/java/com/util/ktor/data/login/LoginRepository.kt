package com.util.ktor.data.login

import com.util.ktor.HttpUtil
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.createLoginModel
import com.util.ktor.data.login.model.UserToken
import com.util.ktor.model.ResultModel
import kotlinx.serialization.json.Json

/**
 * @description
 * @author 杨帅林
 * @create 2024/10/18 15:43
 **/
class LoginRepository(
    private val httpUtil: HttpUtil,
    private val json: Json,
    private val config: NetworkConfigProvider
) {
     suspend fun passwordLogin(host:String = "",username: String,password:String) : ResultModel<UserToken> {
  /*       return json.decodeFromString<ResultModel<UserToken>>("{\n" +
                 "    \"code\":401,\n" +
                 "    \"msg\":\"\",\n" +
                 "    \"data\":{\n" +
                 "        \n" +
                 "    }\n" +
                 "}")*/
         return httpUtil.post<UserToken>(
             path = host + config.loginPath,
             body = createLoginModel(json = json, style = config.getLoginKeyStyle(), username = username, password = password)
         )
     }
}