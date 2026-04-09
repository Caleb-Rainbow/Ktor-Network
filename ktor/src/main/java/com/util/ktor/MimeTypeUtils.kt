package com.util.ktor

import java.io.File

private val MIME_TYPES = mapOf(
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "bmp" to "image/bmp",
    "svg" to "image/svg+xml",
    "pdf" to "application/pdf",
    "doc" to "application/msword",
    "docx" to "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    "xls" to "application/vnd.ms-excel",
    "xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "mp4" to "video/mp4",
    "avi" to "video/x-msvideo",
    "mp3" to "audio/mpeg",
    "wav" to "audio/wav",
    "txt" to "text/plain",
    "json" to "application/json",
    "zip" to "application/zip",
    "apk" to "application/vnd.android.package-archive",
    "pptx" to "application/vnd.openxmlformats-officedocument.presentationml.presentation",
    "ppt" to "application/vnd.ms-powerpoint",
    "rar" to "application/vnd.rar",
    "7z" to "application/x-7z-compressed",
    "wps" to "application/kswps",
    "et" to "application/kset",
    "dps" to "application/ksdps",
)

internal fun File.contentType(): String {
    val ext = extension.lowercase()
    return MIME_TYPES[ext] ?: "application/octet-stream"
}
