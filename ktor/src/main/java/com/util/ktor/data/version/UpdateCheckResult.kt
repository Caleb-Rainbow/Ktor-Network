package com.util.ktor.data.version

import com.util.ktor.data.version.model.Version
import com.util.ktor.model.ResultModel

sealed interface UpdateCheckResult {
    val result: ResultModel<Version>

    data class External(override val result: ResultModel<Version>) : UpdateCheckResult

    data class Internal(override val result: ResultModel<Version>) : UpdateCheckResult
}
