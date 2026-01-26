# Ktor-Network

基于 Kotlin + Ktor 技术栈开发的企业级网络通讯库，提供简洁、高效、安全的后端接口协议通讯解决方案。

## 目录

- [项目概述](#项目概述)
- [技术架构](#技术架构)
- [快速开始](#快速开始)
- [核心API说明](#核心api说明)
- [接口调用示例](#接口调用示例)
- [错误处理机制](#错误处理机制)
- [配置选项](#配置选项)
- [测试方法](#测试方法)
- [贡献指南](#贡献指南)

---

## 项目概述

### 用途

Ktor-Network 是一个专为 Android 平台设计的网络通讯库，封装了 Ktor 客户端的强大功能，提供了开箱即用的企业级网络请求解决方案。该库特别适用于需要与后端 RESTful API 进行交互的应用场景。

### 核心功能

| 功能 | 描述 |
|------|------|
| **HTTP 请求封装** | 支持 GET、POST、PUT、DELETE 等 HTTP 方法 |
| **文件上传/下载** | 支持大文件上传和下载，提供进度回调 |
| **自动 Token 刷新** | 智能检测 Token 过期并自动刷新，支持并发请求处理 |
| **Bearer 认证** | 内置 Bearer Token 认证机制 |
| **JSON 序列化** | 基于 kotlinx.serialization 的自动序列化/反序列化 |
| **错误处理** | 统一的错误处理和异常映射机制 |
| **日志记录** | 详细的请求/响应日志，便于调试 |
| **依赖注入** | 集成 Koin 依赖注入框架 |

### 核心优势

- **类型安全**：利用 Kotlin 的类型系统和泛型确保编译时安全
- **协程支持**：基于 Kotlin 协程的异步编程模型
- **自动重试**：Token 过期自动刷新并重试失败的请求
- **灵活配置**：通过接口实现自定义配置
- **易于测试**：提供 MockEngine 支持单元测试
- **轻量级**：最小化依赖，按需引入

---

## 技术架构

### Kotlin 语言特性应用

```kotlin
// 1. 扩展函数 - 提供便捷的 API
suspend inline fun <reified T> HttpUtil.get(
    path: String,
    parametersMap: Map<String, String> = emptyMap()
): ResultModel<T>

// 2. 高阶函数 - 灵活的请求构建
suspend fun <T> executeRequest(
    serializer: KSerializer<ResultModel<T>>,
    block: HttpRequestBuilder.() -> Unit
): ResultModel<T>

// 3. 协程 - 异步非阻塞操作
suspend fun downloadFile(
    path: String,
    filePath: String,
    onProgress: (progress: Float, speed: String, remainingTime: String) -> Unit
): ResultModel<String>

// 4. 数据类 - 简洁的数据模型
@Serializable
data class ResultModel<T>(
    val code: Int,
    val message: String?,
    val data: T? = null
)
```

### Ktor 框架使用说明

#### 核心插件配置

| 插件 | 功能 | 配置示例 |
|------|------|----------|
| **ContentNegotiation** | 自动序列化/反序列化 | `install(ContentNegotiation) { json(json) }` |
| **Auth** | Bearer Token 认证 | `install(Auth) { bearer { ... } }` |
| **HttpTimeout** | 请求超时设置 | `install(HttpTimeout) { requestTimeoutMillis = 30000 }` |
| **Logging** | 请求/响应日志 | `install(Logging) { level = LogLevel.BODY }` |
| **DefaultRequest** | 默认请求配置 | `install(DefaultRequest) { ... }` |
| **CustomAuthTriggerPlugin** | 自定义认证触发器 | `install(CustomAuthTriggerPlugin) { ... }` |

#### HttpClient 引擎

```kotlin
// 使用 CIO 引擎（推荐用于 Android）
HttpClient(CIO) {
    installPlugins(config, json)
}

// 可选：使用 OkHttp 引擎
HttpClient(OkHttp) {
    installPlugins(config, json)
}
```

---

## 快速开始

### 环境要求

| 项目 | 要求 |
|------|------|
| **Android SDK** | API 26 (Android 8.0) 及以上 |
| **Kotlin** | 2.3.0 |
| **Gradle** | 9.1.0+ |
| **JDK** | 17 |
| **Android Gradle Plugin** | 8.13.2+ |

### 依赖配置

#### 1. 在项目根目录的 `settings.gradle.kts` 中添加仓库：

```kotlin
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}
```

#### 2. 在模块的 `build.gradle.kts` 中添加依赖：

```kotlin
dependencies {
    implementation("com.github.Caleb-Rainbow:Ktor-Network:1.0.4")
}
```

### 初始化示例

#### 步骤 1：实现 NetworkConfigProvider 接口

```kotlin
import com.util.ktor.config.NetworkConfigProvider
import com.util.ktor.config.LoginKeyStyle

class MyNetworkConfigProvider : NetworkConfigProvider {
    override val serverAddress: String = "https://api.example.com"
    override val serverPort: String = "443"
    override var token: String = ""
        get() = SharedPreferencesManager.getToken()
        set(value) = SharedPreferencesManager.saveToken(value)

    override val tenant: String
        get() = SharedPreferencesManager.getTenant()

    override val username: String = "your_username"
    override val password: String = "your_password"
    override val loginPath: String = "/api/auth/login"
    override val uploadFilePath: String = "/api/file/upload"
    override val checkUpdatePath: String = "/api/version/check"
    override val heartBeatPath: String = "/api/heartbeat"
    override val bucketName: String = "my-bucket"

    override fun onNewTokenReceived(token: String, tenant: String?) {
        this.token = token
        tenant?.let { SharedPreferencesManager.saveTenant(it) }
    }

    override fun getLoginKeyStyle(): LoginKeyStyle = LoginKeyStyle.CAMEL_CASE_V1
}
```

#### 步骤 2：配置 Koin 依赖注入（推荐）

```kotlin
import org.koin.dsl.module
import com.util.ktor.HttpUtil
import com.util.ktor.createDefaultHttpClient
import com.util.ktor.data.login.LoginRepository
import com.util.ktor.data.file.FileRepository
import com.util.ktor.data.version.VersionRepository
import kotlinx.serialization.json.Json

val networkModule = module {
    single<NetworkConfigProvider> { MyNetworkConfigProvider() }

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            prettyPrint = false
        }
    }

    single {
        HttpUtil(
            httpClient = createDefaultHttpClient(get(), get()),
            json = get(),
            config = get()
        )
    }

    single { LoginRepository(httpUtil = get(), json = get(), config = get()) }
    single { FileRepository(httpUtil = get(), config = get()) }
    single { VersionRepository(httpUtil = get(), config = get()) }
    single { PersonalizationRepository(httpUtil = get(), config = get()) }
    single { HeartRepository(httpUtil = get(), config = get()) }
}
```

#### 步骤 3：在 Application 中初始化

```kotlin
import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MyApplication)
            modules(networkModule)
        }
    }
}
```

#### 步骤 4：在 AndroidManifest.xml 中注册 Application

```xml
<application
    android:name=".MyApplication"
    ...>
    ...
</application>
```

---

## 核心API说明

### HttpUtil

主要的 HTTP 请求工具类，提供所有网络请求功能。

#### 主要方法

| 方法 | 参数 | 返回值 | 描述 |
|------|------|--------|------|
| `get()` | `path: String`, `parametersMap: Map<String, String>` | `ResultModel<T>` | 执行 GET 请求 |
| `post()` | `path: String`, `body: Any?`, `parametersMap: Map<String, String>` | `ResultModel<T>` | 执行 POST 请求 |
| `put()` | `path: String`, `body: Any?`, `parametersMap: Map<String, String>` | `ResultModel<T>` | 执行 PUT 请求 |
| `delete()` | `path: String`, `body: Any?`, `parametersMap: Map<String, String>` | `ResultModel<T>` | 执行 DELETE 请求 |
| `uploadFile()` | `file: File`, `timeoutMillis: Long` | `ResultModel<JsonObject>` | 上传文件 |
| `downloadFile()` | `path: String`, `filePath: String`, `timeoutMillis: Long`, `onProgress: (Float, String, String) -> Unit` | `ResultModel<String>` | 下载文件 |

#### 方法参数详解

##### get()

```kotlin
suspend inline fun <reified T> get(
    path: String,
    parametersMap: Map<String, String> = emptyMap()
): ResultModel<T>
```

- **path**: 请求路径，可以是完整 URL 或相对路径
- **parametersMap**: URL 查询参数，键值对形式
- **返回值**: `ResultModel<T>` 包含响应数据

##### post()

```kotlin
suspend inline fun <reified T> post(
    path: String,
    body: Any? = null,
    parametersMap: Map<String, String> = emptyMap()
): ResultModel<T>
```

- **path**: 请求路径
- **body**: 请求体，支持 `@Serializable` 标注的数据类
- **parametersMap**: URL 查询参数
- **返回值**: `ResultModel<T>` 包含响应数据

##### uploadFile()

```kotlin
suspend fun uploadFile(
    file: File,
    timeoutMillis: Long = 300_000L
): ResultModel<JsonObject>
```

- **file**: 要上传的文件对象
- **timeoutMillis**: 超时时间（毫秒），默认 5 分钟
- **返回值**: `ResultModel<JsonObject>` 包含上传结果

##### downloadFile()

```kotlin
suspend fun downloadFile(
    path: String,
    filePath: String,
    timeoutMillis: Long = 1_200_000L,
    onProgress: (progress: Float, speed: String, remainingTime: String) -> Unit
): ResultModel<String>
```

- **path**: 文件下载 URL
- **filePath**: 本地保存路径
- **timeoutMillis**: 超时时间（毫秒），默认 20 分钟
- **onProgress**: 进度回调
  - `progress`: 下载进度（0.0 - 1.0）
  - `speed`: 下载速度（MB/s）
  - `remainingTime`: 剩余时间（格式化字符串）
- **返回值**: `ResultModel<String>` 包含本地文件路径

### ResultModel

统一的响应数据模型。

```kotlin
@Serializable
data class ResultModel<T>(
    val code: Int,
    @SerialName("msg")
    val message: String?,
    val data: T? = null,
    val rows: List<T>? = null,
    val total: Int? = null,
    @SerialName("img")
    val image: String? = null,
    val token: UserToken? = null,
    val uuid: String? = null,
    val url: String? = null
)
```

#### 伴生对象方法

```kotlin
// 创建成功响应
ResultModel.success(data: T)

// 创建错误响应
ResultModel.error(message: String = "未知错误")
```

#### 实例方法

```kotlin
// 判断是否成功
result.isSuccess(): Boolean

// 判断是否失败
result.isError(): Boolean
```

### NetworkConfigProvider

网络配置接口，用于自定义网络请求配置。

```kotlin
interface NetworkConfigProvider {
    val serverAddress: String      // 服务器地址
    val serverPort: String          // 服务器端口
    var token: String               // 访问令牌
    val tenant: String              // 租户标识
    val username: String           // 用户名
    val password: String           // 密码
    val loginPath: String          // 登录路径
    val uploadFilePath: String      // 文件上传路径
    val checkUpdatePath: String    // 版本检查路径
    val heartBeatPath: String      // 心跳路径
    val bucketName: String         // 存储桶名称
    val getLogoPath: String        // Logo 获取路径（默认为 "https://vis.xingchenwulian.com/deviceLogo/selectDeviceLogo"）

    // Token 更新回调
    fun onNewTokenReceived(token: String, tenant: String?)

    // 获取登录字段命名风格，默认为 CAMEL_CASE_V1
    fun getLoginKeyStyle(): LoginKeyStyle
}
```

### LoginKeyStyle

登录请求体字段命名风格枚举。

```kotlin
enum class LoginKeyStyle {
    // 风格1: { "userName": "...", "passWord": "..." }
    CAMEL_CASE_V1,

    // 风格2: { "username": "...", "password": "..." }
    LOWER_CASE_V2
}
```

### CustomAuthTriggerPlugin

自定义认证触发插件，用于检测业务层的 Token 过期并触发刷新。

```kotlin
install(CustomAuthTriggerPlugin) {
    json = this@installPlugins
    tokenExpiredCode = 401  // 自定义 Token 过期业务码
}
```

### Repository 类

#### LoginRepository

处理登录相关请求。

```kotlin
class LoginRepository(
    private val httpUtil: HttpUtil,
    private val json: Json,
    private val config: NetworkConfigProvider
)

suspend fun passwordLogin(
    host: String = "",
    username: String,
    password: String
): ResultModel<UserToken>
```

#### FileRepository

处理文件相关请求。

```kotlin
class FileRepository(
    private val httpUtil: HttpUtil,
    private val config: NetworkConfigProvider
)

suspend fun uploadFile(file: File): ResultModel<JsonObject>
```

#### VersionRepository

处理版本相关请求。

```kotlin
class VersionRepository(
    private val httpUtil: HttpUtil,
    private val config: NetworkConfigProvider
)

suspend fun checkUpdate(): ResultModel<Version>

suspend fun downloadFile(
    path: String,
    filePath: String,
    onProgress: (progress: Float, speed: String, remainingTime: String) -> Unit
): ResultModel<String>
```

#### PersonalizationRepository

处理个性化配置相关请求。

```kotlin
class PersonalizationRepository(
    private val httpUtil: HttpUtil,
    private val config: NetworkConfigProvider
)

suspend fun getLogoUrl(deviceNumber: String): ResultModel<LogoModel>
```

#### HeartRepository

处理心跳相关请求。

```kotlin
class HeartRepository(
    private val httpUtil: HttpUtil,
    private val config: NetworkConfigProvider
)

suspend fun heartbeat(deviceNumber: String, second: Int): ResultModel<String>
```

---

## 接口调用示例

### 同步请求（在协程作用域中）

```kotlin
// 在 ViewModel 或 Repository 中
class MyViewModel(
    private val httpUtil: HttpUtil
) : ViewModel() {

    fun fetchData() {
        viewModelScope.launch {
            try {
                val result = httpUtil.get<UserData>(
                    path = "/api/user/profile",
                    parametersMap = mapOf("userId" to "123")
                )

                if (result.isSuccess()) {
                    val userData = result.data
                    // 处理成功响应
                } else {
                    // 处理业务错误
                    println("Error: ${result.message}")
                }
            } catch (e: Exception) {
                // 处理异常
                println("Exception: ${e.message}")
            }
        }
    }
}
```

### 异步请求（使用协程）

```kotlin
// 在 Activity 或 Fragment 中
class MainActivity : AppCompatActivity() {

    private val httpUtil: HttpUtil by inject()

    private fun loadData() {
        lifecycleScope.launch {
            // 显示加载状态
            showLoading()

            val result = httpUtil.get<List<Item>>(
                path = "/api/items",
                parametersMap = mapOf("page" to "1", "size" to "20")
            )

            // 隐藏加载状态
            hideLoading()

            if (result.isSuccess()) {
                val items = result.data ?: emptyList()
                updateUI(items)
            } else {
                showError(result.message ?: "加载失败")
            }
        }
    }
}
```

### POST 请求示例

```kotlin
@Serializable
data class CreateOrderRequest(
    val productId: String,
    val quantity: Int,
    val address: String
)

@Serializable
data class OrderResponse(
    val orderId: String,
    val status: String
)

class OrderRepository(
    private val httpUtil: HttpUtil
) {
    suspend fun createOrder(request: CreateOrderRequest): ResultModel<OrderResponse> {
        return httpUtil.post<OrderResponse>(
            path = "/api/orders",
            body = request
        )
    }
}
```

### 文件上传示例

```kotlin
class UploadViewModel(
    private val fileRepository: FileRepository
) : ViewModel() {

    fun uploadImage(file: File) {
        viewModelScope.launch {
            _uploadState.value = UploadState.Loading

            val result = fileRepository.uploadFile(file)

            if (result.isSuccess()) {
                _uploadState.value = UploadState.Success(result.data)
            } else {
                _uploadState.value = UploadState.Error(result.message ?: "上传失败")
            }
        }
    }
}
```

### 文件下载示例

```kotlin
class DownloadViewModel(
    private val versionRepository: VersionRepository
) : ViewModel() {

    fun downloadApk(url: String) {
        viewModelScope.launch {
            val filePath = "${getExternalFilesDir(null)}/update.apk"

            val result = versionRepository.downloadFile(
                path = url,
                filePath = filePath
            ) { progress, speed, remainingTime ->
                // 更新进度
                _downloadProgress.value = DownloadProgress(
                    progress = progress,
                    speed = speed,
                    remainingTime = remainingTime
                )
            }

            if (result.isSuccess()) {
                _downloadState.value = DownloadState.Completed(result.data)
            } else {
                _downloadState.value = DownloadState.Error(result.message ?: "下载失败")
            }
        }
    }
}
```

### 使用 Repository 示例

```kotlin
class MyViewModel(
    private val loginRepository: LoginRepository,
    private val versionRepository: VersionRepository
) : ViewModel() {

    fun login(username: String, password: String) {
        viewModelScope.launch {
            val result = loginRepository.passwordLogin(
                username = username,
                password = password
            )

            if (result.isSuccess()) {
                val userToken = result.data
                // 保存 Token 并跳转到主页面
                saveToken(userToken?.token ?: "")
                navigateToMain()
            } else {
                showLoginError(result.message ?: "登录失败")
            }
        }
    }

    fun checkForUpdates() {
        viewModelScope.launch {
            val result = versionRepository.checkUpdate()

            if (result.isSuccess()) {
                val version = result.data
                if (version?.hasUpdate == true) {
                    showUpdateDialog(version)
                }
            }
        }
    }
}
```

### 并发请求示例

```kotlin
class DashboardViewModel(
    private val httpUtil: HttpUtil
) : ViewModel() {

    fun loadDashboardData() {
        viewModelScope.launch {
            // 并发执行多个请求
            val deferred1 = async { httpUtil.get<UserInfo>("/api/user/info") }
            val deferred2 = async { httpUtil.get<List<Notification>>("/api/notifications") }
            val deferred3 = async { httpUtil.get<Statistics>("/api/statistics") }

            // 等待所有请求完成
            val userInfo = deferred1.await()
            val notifications = deferred2.await()
            val statistics = deferred3.await()

            // 处理所有数据
            updateDashboard(userInfo, notifications, statistics)
        }
    }
}
```

---

## 错误处理机制

### 自定义错误码

| 错误码 | 常量名 | 描述 |
|--------|--------|------|
| 900 | `UNKNOWN_ERROR` | 未知错误 |
| 901 | `TIMEOUT_ERROR` | 网络请求超时 |
| 902 | `CONNECTION_ERROR` | 网络连接异常（如 DNS 解析失败） |
| 903 | `SERIALIZATION_ERROR` | 数据解析异常 |

### HTTP 状态码

| 状态码 | 枚举值 | 描述 |
|--------|--------|------|
| 200 | `OK` | 请求成功 |
| 401 | `NO_LOGIN` | 未登录或 Token 过期 |
| 403 | `NO_PERMISSION` | 无权限访问 |
| 404 | `NOT_FOUND` | 资源不存在 |
| 500 | `INTERNAL_SERVER_ERROR` | 服务器内部错误 |
| 501 | `ERROR` | 通用错误 |

### 异常处理示例

```kotlin
class ErrorHandler {
    fun handleError(result: ResultModel<*>) {
        when (result.code) {
            CustomResultCode.TIMEOUT_ERROR -> {
                showToast("网络请求超时，请检查网络连接")
            }
            CustomResultCode.CONNECTION_ERROR -> {
                showToast("无法连接到服务器，请稍后重试")
            }
            CustomResultCode.SERIALIZATION_ERROR -> {
                showToast("数据解析失败，请联系技术支持")
            }
            ResultCodeType.NO_LOGIN.code -> {
                // Token 过期，跳转到登录页
                navigateToLogin()
            }
            ResultCodeType.NO_PERMISSION.code -> {
                showToast("您没有权限执行此操作")
            }
            else -> {
                showToast(result.message ?: "操作失败")
            }
        }
    }
}
```

### 全局异常捕获

```kotlin
class GlobalExceptionHandler : Thread.UncaughtExceptionHandler {
    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        when (throwable) {
            is HttpRequestTimeoutException -> {
                // 处理超时异常
                logError("Request timeout", throwable)
            }
            is ConnectTimeoutException -> {
                // 处理连接超时异常
                logError("Connection timeout", throwable)
            }
            is UnresolvedAddressException -> {
                // 处理地址解析异常
                logError("Unresolved address", throwable)
            }
            is SerializationException -> {
                // 处理序列化异常
                logError("Serialization error", throwable)
            }
            else -> {
                // 处理其他异常
                logError("Unknown error", throwable)
            }
        }
    }
}
```

---

## 配置选项

### HttpClient 配置

```kotlin
fun createDefaultHttpClient(config: NetworkConfigProvider, json: Json): HttpClient {
    return HttpClient(CIO) {
        installPlugins(config, json)
    }
}
```

### 超时配置

```kotlin
install(HttpTimeout) {
    requestTimeoutMillis = 30000   // 请求超时：30秒
    connectTimeoutMillis = 30000  // 连接超时：30秒
    socketTimeoutMillis = 30000   // Socket 超时：30秒
}
```

### 日志配置

```kotlin
install(Logging) {
    logger = Logger.ANDROID           // 使用 Android 日志记录器
    level = LogLevel.ALL              // 记录完整的请求和响应信息，便于调试
    // 可选：过滤敏感信息
    sanitizeHeader { header -> header == HttpHeaders.Authorization }
}
```

### JSON 配置

```kotlin
val json = Json {
    ignoreUnknownKeys = true    // 忽略未知字段
    isLenient = true            // 宽松模式
    prettyPrint = false         // 不格式化输出
    encodeDefaults = false      // 不编码默认值
    coerceInputValues = true    // 强制转换输入值
}
```

### 认证配置

```kotlin
install(Auth) {
    bearer {
        // 设置 realm，这对于 Auth 插件正确识别和响应 401 挑战是必要的
        realm = "Access to protected resources"
        
        // 加载 Token - 每次请求前动态加载最新的 token
        loadTokens {
            val token = config.token
            if (token.isNotEmpty()) {
                BearerTokens(accessToken = token, refreshToken = null)
            } else {
                null
            }
        }

        // 智能的请求发送策略
        sendWithoutRequest { request ->
            val isLoginPath = request.url.encodedPath == config.loginPath
            val hasToken = config.token.isNotEmpty()
            // 对登录请求不发送 token，对其他请求：有 token 时才发送
            !isLoginPath && hasToken
        }

        // 增强的 Token 刷新机制
        refreshTokens {
            // 使用全局锁来确保只有一个刷新操作在执行
            AuthRefreshLock.mutex.withLock {
                val oldTokens = this.oldTokens
                val currentSavedToken = config.token
                
                // 检查 Token 是否已被外部更新
                if (currentSavedToken.isNotEmpty() && oldTokens?.accessToken != currentSavedToken) {
                    BearerTokens(accessToken = currentSavedToken, refreshToken = null)
                } else {
                    // 执行登录刷新
                    val loginPayload = createLoginModel(json, config.getLoginKeyStyle(), config.username, config.password)
                    val response = client.post(config.loginPath) {
                        markAsRefreshTokenRequest()
                        setBody(loginPayload)
                        contentType(ContentType.Application.Json)
                    }
                    val loginResult = response.body<ResultModel<UserToken>>()
                    if (loginResult.isSuccess() && loginResult.token != null) {
                        config.onNewTokenReceived(loginResult.token.token, loginResult.token.tenant)
                        BearerTokens(accessToken = loginResult.token.token, refreshToken = null)
                    } else {
                        null
                    }
                }
            }
        }
    }
}
```

### 自定义序列化器

```kotlin
// BigDecimal 序列化器
object BigDecimalSerializer : KSerializer<BigDecimal> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("BigDecimal", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: BigDecimal) {
        encoder.encodeString(value.toPlainString())
    }

    override fun deserialize(decoder: Decoder): BigDecimal {
        return BigDecimal(decoder.decodeString())
    }
}

// 使用自定义序列化器
val json = Json {
    serializersModule = SerializersModule {
        contextual(BigDecimal::class, BigDecimalSerializer)
    }
}
```

---

## 测试方法

### 单元测试

项目使用 JUnit 4 和 Ktor MockEngine 进行单元测试。

#### 测试示例

```kotlin
class HttpUtilKtTest : KoinTest {

    @get:Rule
    val koinTestRule = KoinTestRule.create {
        printLogger()
        modules(networkModule)
    }

    @Test
    fun `createLoginModel with CAMEL CASE V1 style`() {
        val configProvider: NetworkConfigProvider = get()
        val json: Json = get()

        val loginModel = createLoginModel(
            json,
            LoginKeyStyle.CAMEL_CASE_V1,
            configProvider.username,
            configProvider.password
        )

        val jsonObject = json.parseToJsonElement(loginModel).jsonObject
        assertTrue(jsonObject.containsKey("userName"))
        assertEquals(configProvider.username, jsonObject["userName"]?.jsonPrimitive?.content)
    }

    @Test
    fun `ContentNegotiation plugin should serialize request and deserialize response`() = runBlocking {
        val mockEngine = MockEngine { request ->
            respond(
                content = """{"id": 2, "message": "Hello from Mock Server"}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(get())
            }
        }

        val response: TestData = client.post("http://localhost/test") {
            setBody(TestData(id = 1, message = "Hello Ktor"))
            contentType(ContentType.Application.Json)
        }.body()

        assertEquals(2, response.id)
        assertEquals("Hello from Mock Server", response.message)

        client.close()
    }
}
```

### 运行测试

```bash
# 运行所有单元测试
./gradlew test

# 运行特定测试类
./gradlew test --tests HttpUtilKtTest

# 运行特定测试方法
./gradlew test --tests "HttpUtilKtTest.createLoginModel with CAMEL CASE V1 style"
```

### 集成测试

```kotlin
@HiltAndroidTest
class NetworkIntegrationTest {

    @Inject
    lateinit var httpUtil: HttpUtil

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Test
    fun testRealApiCall() = runBlocking {
        val result = httpUtil.get<UserData>("/api/user/profile")

        assertTrue(result.isSuccess())
        assertNotNull(result.data)
    }
}
```

### 测试覆盖率

```bash
# 生成测试覆盖率报告
./gradlew testDebugUnitTestCoverage

# 查看报告
open app/build/reports/coverage/test/index.html
```

---

## 贡献指南

### 开发规范

#### 代码风格

- 遵循 Kotlin 官方代码风格指南
- 使用 4 空格缩进
- 每行最大长度 120 字符
- 使用有意义的变量和函数命名

#### 提交规范

提交信息格式：

```
<type>(<scope>): <subject>

<body>

<footer>
```

类型（type）：
- `feat`: 新功能
- `fix`: 修复 bug
- `docs`: 文档更新
- `style`: 代码格式调整
- `refactor`: 重构
- `test`: 测试相关
- `chore`: 构建/工具链相关

示例：

```
feat(http): add support for custom timeout configuration

Add a new parameter to HttpUtil methods to allow custom timeout values
for individual requests.

Closes #123
```

#### Pull Request 流程

1. Fork 本仓库
2. 创建特性分支：`git checkout -b feature/amazing-feature`
3. 提交更改：`git commit -m 'feat: add amazing feature'`
4. 推送到分支：`git push origin feature/amazing-feature`
5. 创建 Pull Request

### 开发环境搭建

```bash
# 克隆仓库
git clone https://github.com/Caleb-Rainbow/Ktor-Network.git
cd Ktor-Network

# 安装依赖
./gradlew build

# 运行测试
./gradlew test

# 生成文档
./gradlew dokka
```

### 报告问题

如果发现 bug 或有功能建议，请：

1. 搜索现有的 Issues
2. 创建新的 Issue，包含：
   - 问题描述
   - 复现步骤
   - 预期行为
   - 实际行为
   - 环境信息（Kotlin 版本、Android 版本等）

### 许可证

本项目采用 MIT 许可证。详见 [LICENSE](LICENSE) 文件。

---

## 联系方式

- **作者**: 杨帅林
- **邮箱**: [your-email@example.com]
- **GitHub**: [Caleb-Rainbow](https://github.com/Caleb-Rainbow)

---

## 更新日志

### Version 1.0.4

- 新增 PersonalizationRepository 个性化配置仓库
- 新增 HeartRepository 心跳功能仓库
- 扩展 NetworkConfigProvider 接口，添加 getLogoPath 属性
- 优化依赖注入模块配置，包含所有新增仓库

### Version 1.0.3

- 升级构建工具：Gradle 9.1.0、Kotlin 2.3.0、AGP 8.13.2
- 增强 Ktor 认证逻辑，添加 realm 属性支持
- 优化 Token 刷新机制，支持外部登录导致的 Token 变更
- 改进调试日志，提升 LogLevel.ALL 以获取完整请求信息
- 现代化构建脚本，使用新的 Android 插件配置语法

### Version 1.0.2

- 优化 Token 刷新机制，支持并发请求
- 添加文件下载进度回调
- 改进错误处理和日志记录

### Version 1.0.1

- 添加 BigDecimal 自定义序列化器
- 支持多种登录字段命名风格
- 优化网络请求超时配置

### Version 1.0.0

- 初始版本发布
- 支持 GET、POST、PUT、DELETE 请求
- 文件上传/下载功能
- 自动 Token 刷新
- Bearer 认证

---

## 相关资源

- [Ktor 官方文档](https://ktor.io/docs/)
- [Kotlin 官方文档](https://kotlinlang.org/docs/)
- [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)
- [Koin 依赖注入](https://insert-koin.io/)
