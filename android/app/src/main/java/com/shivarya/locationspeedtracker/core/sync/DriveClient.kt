package com.shivarya.locationspeedtracker.core.sync

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/** A journey file in the app folder: [sessionId] and the session's [updatedAt] when it was uploaded. */
data class RemoteJourney(val fileId: String, val sessionId: String, val updatedAt: Long)

class DriveException(val code: Int, message: String) : IOException("Drive $code: $message")

/**
 * The few Drive v3 REST calls journey sync needs, on the app's OkHttp. Everything lives in `appDataFolder`, invisible
 * in the Drive UI; each file carries its session id and version in appProperties, so listing needs no downloads.
 */
class DriveClient(private val http: OkHttpClient, private val token: String) {
    fun email(): String? = call(get("$API/about?fields=user(emailAddress)")) { json.decodeFromString(About.serializer(), it).user?.emailAddress }

    fun list(): List<RemoteJourney> {
        val out = ArrayList<RemoteJourney>()
        var page: String? = null
        do {
            val url = "$API/files?spaces=appDataFolder&pageSize=1000&fields=nextPageToken,files(id,appProperties)" +
                (page?.let { "&pageToken=$it" } ?: "")
            val res = call(get(url)) { json.decodeFromString(FileList.serializer(), it) }
            res.files.forEach { f ->
                val id = f.appProperties?.get(PROP_SESSION) ?: return@forEach
                out += RemoteJourney(f.id, id, f.appProperties[PROP_UPDATED]?.toLongOrNull() ?: 0L)
            }
            page = res.nextPageToken
        } while (page != null)
        return out
    }

    /** Creates the file, or replaces [fileId]'s content and version. Returns the file id. */
    fun put(fileId: String?, sessionId: String, updatedAt: Long, gzipped: ByteArray): String {
        val meta = buildString {
            append("{\"name\":\"$sessionId.json.gz\",")
            if (fileId == null) append("\"parents\":[\"appDataFolder\"],")
            append("\"appProperties\":{\"$PROP_SESSION\":\"$sessionId\",\"$PROP_UPDATED\":\"$updatedAt\"}}")
        }
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(meta.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .addPart(gzipped.toRequestBody(GZIP))
            .build()
        val req = if (fileId == null) {
            builder("$UPLOAD/files?uploadType=multipart&fields=id").post(body)
        } else {
            builder("$UPLOAD/files/$fileId?uploadType=multipart&fields=id").patch(body)
        }
        return call(req.build()) { json.decodeFromString(Created.serializer(), it).id }
    }

    fun download(fileId: String): ByteArray = http.newCall(get("$API/files/$fileId?alt=media")).execute().use { r ->
        if (!r.isSuccessful) throw DriveException(r.code, r.body?.string().orEmpty().take(200))
        r.body?.bytes() ?: ByteArray(0)
    }

    fun delete(fileId: String) {
        http.newCall(builder("$API/files/$fileId").delete().build()).execute().use { r ->
            if (!r.isSuccessful && r.code != 404) throw DriveException(r.code, r.body?.string().orEmpty().take(200))
        }
    }

    private fun builder(url: String) = Request.Builder().url(url).header("Authorization", "Bearer $token")
    private fun get(url: String) = builder(url).get().build()

    private fun <T> call(req: Request, parse: (String) -> T): T = http.newCall(req).execute().use { r ->
        val text = r.body?.string().orEmpty()
        if (!r.isSuccessful) throw DriveException(r.code, text.take(200))
        parse(text)
    }

    @Serializable private data class About(val user: User? = null)
    @Serializable private data class User(val emailAddress: String? = null)
    @Serializable private data class FileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)
    @Serializable private data class DriveFile(val id: String, val appProperties: Map<String, String>? = null)
    @Serializable private data class Created(val id: String)

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val PROP_SESSION = "sessionId"
        const val PROP_UPDATED = "updatedAt"
        val GZIP = "application/gzip".toMediaType()
        val json = Json { ignoreUnknownKeys = true }
    }
}

