# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Ktor-Network is an enterprise-grade Android networking library built on Kotlin + Ktor. It wraps Ktor's HTTP client with token refresh, file upload/download, and JSON serialization. Published as `com.github.Caleb-Rainbow:Ktor-Network`.

## Build & Test Commands

**JDK:** Use `D:\AndroidStudio\jbr` (JDK 21). Set `JAVA_HOME` if needed.

```bash
# Build the ktor library module
./gradlew :ktor:assembleRelease

# Run all ktor unit tests
./gradlew :ktor:testDebugUnitTest

# Run a single test class
./gradlew :ktor:testDebugUnitTest --tests "com.util.ktor.HttpUtilKtTest"

# Run a specific test method
./gradlew :ktor:testDebugUnitTest --tests "com.util.ktor.HttpUtilKtTest.testLoginModelV1"

# Build app module (sample consumer)
./gradlew :app:assembleDebug

# Publish to Maven Local
./gradlew :ktor:publishToMavenLocal
```

## Architecture

Two Gradle modules: `:app` (sample consumer) and `:ktor` (the library).

### Core Flow

```
NetworkConfigProvider (interface)  ← Consumer implements this
        ↓
HttpClientFactory                  ← Creates HttpClient with plugins
        ↓
HttpUtil                           ← Primary API surface (get/post/put/delete/upload/download)
        ↓
ResultModel<T>                     ← Generic response wrapper (code/message/data/rows/total)
```

### Key Packages in `com.util.ktor`

| Package | Purpose |
|---------|---------|
| root (`HttpUtil.kt`, `HttpClientFactory.kt`) | HTTP client creation and request execution |
| `config/` | `NetworkConfigProvider` interface — the central configuration contract |
| `plugin/` | `CustomAuthTriggerPlugin` — rewrites 200 responses with expired-token codes to HTTP 401 |
| `model/` | `ResultModel<T>`, `NetworkResult` (custom error codes 900–903) |
| `data/login/` | `LoginRepository` + `UserToken` model |
| `data/file/` | `FileRepository` — delegates to `HttpUtil.uploadFile` |
| `data/version/` | `VersionRepository` — app update check + file download |
| `data/personalization/` | `PersonalizationRepository` — device logo |
| `data/heart/` | `HeartRepository` — heartbeat |
| `serializer/` | `BigDecimalSerializer` |

### Critical Design Details

- **Token refresh**: Uses Ktor Auth plugin + `AuthRefreshLock` (Mutex) for single-writer concurrency. `CustomAuthTriggerPlugin` intercepts HTTP 200 responses where the JSON `code` field indicates token expiry, rewriting status to 401 to trigger Ktor's built-in refresh pipeline. Uses `@InternalAPI`.
- **OkHttp engine**: HTTP/1.1 only (`okhttp3.Protocol.HTTP_1_1`).
- **File upload**: 200MB hard limit, MIME detection via `MimeTypeUtils`.
- **File download**: Streaming with progress callbacks, path traversal protection (`canonicalPath != absolutePath`).
- **Dependencies are `api` scope** — all transitive to consumers.

## Build Configuration

- Gradle 9.1.0, AGP 8.13.2, Kotlin 2.3.20
- compileSdk/targetSdk = 36, minSdk = 26
- Java 21 source/target compatibility, JVM 21 target
- `unitTests.isReturnDefaultValues = true` — Android API stubs return defaults in JVM tests

## Key Dependencies

| Library | Version | Scope |
|---------|---------|-------|
| Ktor (core/okhttp/content-negotiation/serialization/auth/logging) | 3.4.2 | api |
| kotlinx-serialization-json | 1.10.0 | api |
| Koin | 4.2.0 | api |
| Ktor MockEngine | 3.4.2 | test |
| kotlinx-coroutines-test | 1.10.1 | test |

## Testing

Tests run as JVM unit tests (no Android emulator needed). Frameworks: JUnit 4, kotlin.test, Koin Test, Ktor MockEngine, kotlinx-coroutines-test.

Test files mirror source package structure. Each repository and model has a dedicated test class.

## Language

Code comments and documentation are in Chinese (简体中文). Commit messages use conventional commits format.
