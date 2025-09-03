package com.util.ktor.config

/**
 * @description
 * @author 杨帅林
 * @create 2025/9/2 17:08
 **/
interface NetworkConfig {
    val serverAddress: String
    val serverPort: String
    val token: String
    val tenant: String
    val username: String
    val password: String
    val loginPath: String
    val uploadFilePath: String

    // 当 Token 更新时，库需要一种方式来通知 App 保存新 Token
    fun onNewTokenReceived(token: String, tenant: String?)
}