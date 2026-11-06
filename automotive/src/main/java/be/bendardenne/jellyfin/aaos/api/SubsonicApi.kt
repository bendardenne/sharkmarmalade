package be.bendardenne.jellyfin.aaos.api

import be.bendardenne.jellyfin.aaos.UserAgentInterceptor
import dev.zt64.subsonic.api.model.SubsonicErrorCode
import dev.zt64.subsonic.api.model.SubsonicException
import dev.zt64.subsonic.client.SubsonicAuth
import dev.zt64.subsonic.client.SubsonicClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * Checks the Subsonic endpoint before credentials are available. Authentication errors prove
 * reachability; protocol, decoding and transport errors still fail the check.
 */
internal suspend fun pingSubsonicServer(serverUrl: String) {
    val reachable = withTimeoutOrNull(10.seconds) {
        createSubsonicApi(serverUrl, clientConfig = { expectSuccess = true }).use { api ->
            try {
                api.ping()
            } catch (e: SubsonicException) {
                // These errors indicated that the server is reachable and the ping is successful
                // (it's only complaining that authentication is missing).
                when (e.code) {
                    SubsonicErrorCode.REQUIRED_PARAMETER_MISSING,
                    SubsonicErrorCode.WRONG_USERNAME_OR_PASSWORD,
                    SubsonicErrorCode.TOKEN_AUTH_NOT_SUPPORTED_LDAP,
                    SubsonicErrorCode.AUTH_METHOD_NOT_SUPPORTED,
                    SubsonicErrorCode.UNAUTHORIZED -> Unit
                    else -> throw e
                }
            }
        }
        true
    }
    if (reachable == null) {
        throw IOException("Subsonic ping timed out after 10 seconds")
    }
}

fun createSubsonicApi(
    serverUrl: String,
    username: String = "",
    password: String = "",
    clientConfig: HttpClientConfig<*>.() -> Unit = {}
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
        engine = OkHttp.create {
            addInterceptor(UserAgentInterceptor)
            addInterceptor(UnsupportedFieldsInterceptor)
        },
        clientConfig = clientConfig
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
