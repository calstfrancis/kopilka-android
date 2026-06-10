package com.kopilka.android.data.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class WebDavResult<out T> {
    data class Success<T>(val value: T) : WebDavResult<T>()
    data class Error(val message: String) : WebDavResult<Nothing>()
    data object AuthFailure : WebDavResult<Nothing>()
    data class Conflict(val remoteEtag: String) : WebDavResult<Nothing>()
}

data class RemoteProps(val etag: String, val lastModified: String)

class WebDavClient(
    private val baseUrl: String,
    username: String,
    password: String,
) {
    private val credentials = okhttp3.Credentials.basic(username, password)
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun url(path: String) = "${baseUrl.trimEnd('/')}/${path.trimStart('/')}"

    fun propfind(path: String): WebDavResult<RemoteProps> {
        val body = """<?xml version="1.0" encoding="utf-8"?>
<D:propfind xmlns:D="DAV:">
  <D:prop><D:getetag/><D:getlastmodified/></D:prop>
</D:propfind>""".toByteArray()

        val req = Request.Builder()
            .url(url(path))
            .addHeader("Authorization", credentials)
            .addHeader("Depth", "0")
            .method("PROPFIND", body.toRequestBody("application/xml".toMediaType()))
            .build()

        return try {
            http.newCall(req).execute().use { resp ->
                when (resp.code) {
                    401 -> WebDavResult.AuthFailure
                    200, 207 -> {
                        val xml = resp.body?.string() ?: ""
                        val etag = Regex("<[^:>]*:?getetag[^>]*>\"?([^\"<]*)\"?</").find(xml)
                            ?.groupValues?.get(1) ?: ""
                        val lastMod = Regex("<[^:>]*:?getlastmodified[^>]*>([^<]*)</").find(xml)
                            ?.groupValues?.get(1) ?: ""
                        WebDavResult.Success(RemoteProps(etag.trim('"'), lastMod))
                    }
                    else -> WebDavResult.Error("PROPFIND failed: HTTP ${resp.code}")
                }
            }
        } catch (e: IOException) {
            WebDavResult.Error("Network error: ${e.message}")
        }
    }

    fun get(path: String): WebDavResult<Pair<ByteArray, String>> {
        val req = Request.Builder()
            .url(url(path))
            .addHeader("Authorization", credentials)
            .build()

        return try {
            http.newCall(req).execute().use { resp ->
                when (resp.code) {
                    401 -> WebDavResult.AuthFailure
                    404 -> WebDavResult.Error("File not found: $path")
                    200 -> {
                        val bytes = resp.body?.bytes() ?: byteArrayOf()
                        val etag = resp.header("ETag")?.trim('"') ?: ""
                        WebDavResult.Success(bytes to etag)
                    }
                    else -> WebDavResult.Error("Download failed: HTTP ${resp.code}")
                }
            }
        } catch (e: IOException) {
            WebDavResult.Error("Network error: ${e.message}")
        }
    }

    fun put(path: String, content: ByteArray, ifMatchEtag: String?): WebDavResult<String> {
        val reqBuilder = Request.Builder()
            .url(url(path))
            .addHeader("Authorization", credentials)
            .put(content.toRequestBody("application/json; charset=utf-8".toMediaType()))

        if (!ifMatchEtag.isNullOrEmpty()) {
            reqBuilder.addHeader("If-Match", "\"$ifMatchEtag\"")
        }

        return try {
            http.newCall(reqBuilder.build()).execute().use { resp ->
                when (resp.code) {
                    401 -> WebDavResult.AuthFailure
                    412 -> {
                        val remoteEtag = when (val r = propfind(path)) {
                            is WebDavResult.Success -> r.value.etag
                            else -> ""
                        }
                        WebDavResult.Conflict(remoteEtag)
                    }
                    200, 201, 204 -> {
                        val etag = resp.header("ETag")?.trim('"') ?: ""
                        WebDavResult.Success(etag)
                    }
                    else -> WebDavResult.Error("Upload failed: HTTP ${resp.code}")
                }
            }
        } catch (e: IOException) {
            WebDavResult.Error("Network error: ${e.message}")
        }
    }

    fun testConnection(remotePath: String): WebDavResult<Unit> {
        val parentPath = remotePath.substringBeforeLast("/", "").ifEmpty { "/" }
        return when (val r = propfind(parentPath)) {
            is WebDavResult.Success -> WebDavResult.Success(Unit)
            is WebDavResult.AuthFailure -> WebDavResult.AuthFailure
            is WebDavResult.Error -> {
                // 404 on the folder still means auth worked
                if (r.message.contains("404") || r.message.contains("not found", ignoreCase = true)) {
                    WebDavResult.Success(Unit)
                } else {
                    r
                }
            }
            else -> WebDavResult.Error("Unexpected result")
        }
    }
}
