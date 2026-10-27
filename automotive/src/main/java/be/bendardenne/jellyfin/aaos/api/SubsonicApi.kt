package be.bendardenne.jellyfin.aaos.api

import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

fun createSubsonicApi(
    serverUrl: String,
    username: String = "",
    password: String = ""
): SubsonicClient {
    var cleanUrl = serverUrl.trim()
    if (!cleanUrl.startsWith("http://") && !cleanUrl.startsWith("https://")) {
        cleanUrl = "http://$cleanUrl"
    }

    cleanUrl = cleanUrl.trimEnd('/')
    return SubsonicClient(
        baseUrl = cleanUrl,
        auth = SubsonicAuth.Token(username, password),
        client = "SharkMarmalade",
        engine = OkHttp.create { addInterceptor(UnsupportedFieldsInterceptor) }
    )
}

/**
 * subsonic-client 1.0.0-beta08 decodes OpenSubsonic `releaseDate`/`originalReleaseDate` as a full
 * LocalDate, but servers (e.g. Navidrome) send partial or empty objects (`{}`), which makes album
 * requests fail. We don't use these fields, so strip them from responses.
 */
private object UnsupportedFieldsInterceptor : Interceptor {
    private val strippedKeys = setOf("releaseDate", "originalReleaseDate")

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val body = response.body ?: return response
        val contentType = body.contentType()
        if (contentType?.subtype?.contains("json") != true) return response

        val text = body.string()
        if (strippedKeys.none { text.contains("\"$it\"") }) {
            return response.newBuilder().body(text.toResponseBody(contentType)).build()
        }

        val cleaned = try {
            strip(Json.parseToJsonElement(text)).toString()
        } catch (_: Exception) {
            text
        }
        return response.newBuilder().body(cleaned.toResponseBody(contentType)).build()
    }

    private fun strip(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.filterKeys { it !in strippedKeys }.mapValues { strip(it.value) }
        )
        is JsonArray -> JsonArray(element.map(::strip))
        else -> element
    }
}
