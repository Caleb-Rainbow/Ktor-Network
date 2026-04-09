package com.util.ktor.config

interface NetworkConfigProvider {
    val serverAddress: String
    val serverPort: String

    /**
     * 当前认证 Token。
     *
     * **线程安全说明**: 此属性会被 Ktor 插件在 IO 线程上并发读取（如 loadTokens、
     * sendWithoutRequest、refreshTokens）。实现类应确保读写操作的线程安全，例如：
     * - 使用 `@Volatile var` 并配合单一写入者
     * - 或使用 `AtomicReference<String>`
     * - 或通过 `StateFlow<String>` 暴露
     */
    var token: String

    val tenant: String

    /**
     * 登录用户名。
     *
     * **安全警告**: 实现类应使用 `EncryptedSharedPreferences` 或 Android Keystore 安全存储此凭据，
     * 避免明文存储在 `SharedPreferences` 或内存中。凭据仅在 Token 刷新时使用，
     * 考虑使用惰性加载（lazy supplier）减少内存中凭据的存活时间。
     */
    val username: String

    /**
     * 登录密码。
     *
     * **安全警告**: 实现类应使用 `EncryptedSharedPreferences` 或 Android Keystore 安全存储此凭据。
     * JVM 中 String 无法安全清零，建议仅在刷新 Token 时才提供密码（通过 lambda supplier），
     * 以减少凭据在内存中的暴露时间。
     */
    val password: String
    val loginPath: String
    val uploadFilePath: String
    val checkUpdatePath: String
    val heartBeatPath: String
    val bucketName: String

    val getLogoPath: String

    val requestTimeoutMillis: Long
        get() = 300_000L

    val connectTimeoutMillis: Long
        get() = 300_000L

    val socketTimeoutMillis: Long
        get() = 300_000L

    /**
     * 是否启用 HTTP 日志。生产环境应设为 `false` 以避免泄露敏感数据。
     */
    val isLogEnabled: Boolean
        get() = false

    fun onNewTokenReceived(token: String, tenant: String?)

    /**
     * Token 刷新（重新登录）失败时的回调。
     *
     * 实现类可在此回调中执行清除本地缓存、跳转登录页等操作。
     * 默认实现为空操作。
     *
     * @param loginResult 重新登录的响应结果，包含错误码和错误信息。
     */
    fun onTokenRefreshFailed(loginResult: com.util.ktor.model.ResultModel<com.util.ktor.data.login.model.UserToken>) {}

    fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.CAMEL_CASE_V1
}
