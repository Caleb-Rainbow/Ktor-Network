package com.util.ktor.config

/**
 * @description
 * @author 杨帅林
 * @create 2025/9/2 17:08
 **/
interface NetworkConfigProvider {
    val serverAddress: String
    val serverPort: String
    val token: String
    val tenant: String
    val username: String
    val password: String
    val loginPath: String
    val uploadFilePath: String
    val checkUpdatePath: String
    val heartBeatPath: String

    val bucketName: String
    val getLogoPath: String
        get() = "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo"

    // 当 Token 更新时，库需要一种方式来通知 App 保存新 Token
    fun onNewTokenReceived(token: String, tenant: String?)

    /**
     * 获取当前应用所需的登录字段名风格。
     * @return LoginKeyStyle 枚举，默认为 V1 风格
     */
    fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.CAMEL_CASE_V1
}