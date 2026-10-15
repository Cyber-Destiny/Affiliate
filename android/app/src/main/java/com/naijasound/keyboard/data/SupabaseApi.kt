package com.naijasound.keyboard.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Talks to Supabase's REST API directly — no SDK, to keep the app small. */
class SupabaseApi(
    baseUrl: String,
    private val apiKey: String,
    private val http: OkHttpClient,
    private val json: Json,
) {
    private val restUrl: HttpUrl? = baseUrl.trimEnd('/').takeIf { it.isNotBlank() }?.let { "$it/rest/v1/".toHttpUrlOrNull() }

    val isConfigured: Boolean = restUrl != null && apiKey.isNotBlank()

    private val clipList = ListSerializer(Clip.serializer())

    suspend fun trending(limit: Int = 30): List<Clip> = getClips {
        addQueryParameter("order", "pinned.desc,send_count.desc,created_at.desc")
        addQueryParameter("limit", limit.toString())
    }

    suspend fun newest(sinceMillis: Long? = null, limit: Int = 30): List<Clip> = getClips {
        if (sinceMillis != null) addQueryParameter("created_at", "gte.${isoUtc(sinceMillis)}")
        addQueryParameter("order", "created_at.desc")
        addQueryParameter("limit", limit.toString())
    }

    suspend fun byCategory(category: String, limit: Int = 60): List<Clip> = getClips {
        addQueryParameter("category", "eq.$category")
        addQueryParameter("order", "pinned.desc,send_count.desc")
        addQueryParameter("limit", limit.toString())
    }

    /** Live clips among [ids]; anything missing has been taken down. */
    suspend fun byIds(ids: Collection<String>): List<Clip> {
        if (ids.isEmpty()) return emptyList()
        return getClips { addQueryParameter("id", "in.(${ids.joinToString(",")})") }
    }

    suspend fun search(query: String, limit: Int = 40): List<Clip> {
        val body = buildJsonObject {
            put("q", query)
            put("max_results", limit)
        }
        val url = endpoint("rpc/search_clips") {
            addQueryParameter("order", "search_rank.asc,send_count.desc")
        }
        val text = execute(request(url).post(body.toString().toRequestBody(JSON)).build())
        return json.decodeFromString(clipList, text)
    }

    suspend fun categories(): List<Category> {
        val url = endpoint("categories") {
            addQueryParameter("select", "id,name,icon,sort_order")
            addQueryParameter("order", "sort_order.asc")
        }
        return json.decodeFromString(ListSerializer(Category.serializer()), execute(request(url).get().build()))
    }

    suspend fun report(clipId: String, reason: String, contactEmail: String?) {
        val body = buildJsonObject {
            put("clip_id", clipId)
            put("reason", reason.take(500))
            if (!contactEmail.isNullOrBlank()) put("contact_email", contactEmail.trim().take(254))
        }
        val url = endpoint("reports")
        execute(
            request(url)
                .header("Prefer", "return=minimal")
                .post(body.toString().toRequestBody(JSON))
                .build(),
        )
    }

    suspend fun incrementPlay(clipId: String) = callCounter("increment_play", clipId)

    suspend fun incrementSend(clipId: String) = callCounter("increment_send", clipId)

    private suspend fun callCounter(function: String, clipId: String) {
        val body = buildJsonObject { put("p_clip_id", clipId) }
        execute(request(endpoint("rpc/$function")).post(body.toString().toRequestBody(JSON)).build())
    }

    private suspend fun getClips(filters: HttpUrl.Builder.() -> Unit): List<Clip> {
        val url = endpoint("clips") {
            addQueryParameter("select", CLIP_COLUMNS)
            addQueryParameter("status", "eq.live")
            filters()
        }
        return json.decodeFromString(clipList, execute(request(url).get().build()))
    }

    private fun endpoint(path: String, query: HttpUrl.Builder.() -> Unit = {}): HttpUrl {
        val base = restUrl ?: throw NotConfiguredException()
        if (apiKey.isBlank()) throw NotConfiguredException()
        return base.newBuilder().addPathSegments(path).apply(query).build()
    }

    private fun request(url: HttpUrl): Request.Builder = Request.Builder()
        .url(url)
        .header("apikey", apiKey)
        .apply {
            // Older "anon" keys are JWTs and also go in the Authorization header;
            // newer "sb_publishable_…" keys must only be sent as apikey.
            if (apiKey.startsWith("eyJ")) header("Authorization", "Bearer $apiKey")
        }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).await().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, "Server error ${response.code}: ${text.take(200)}")
            text
        }
    }

    private fun isoUtc(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    private companion object {
        val JSON = "application/json".toMediaType()
        const val CLIP_COLUMNS =
            "id,slug,title,category,tags,mp4_url,mp3_url,thumbnail_url,duration_sec,play_count,send_count,pinned,created_at"
    }
}
