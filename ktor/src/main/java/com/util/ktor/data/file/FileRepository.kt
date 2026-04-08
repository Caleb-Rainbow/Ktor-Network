package com.util.ktor.data.file

import com.util.ktor.HttpUtil
import java.io.File

class FileRepository(private val httpUtil: HttpUtil) {
    suspend fun uploadFile(file: File) = httpUtil.uploadFile(file)
}
