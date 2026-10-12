package io.github.gitjacobbit.whoscalling.data

import io.github.gitjacobbit.whoscalling.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class AvailableUpdate(val version: String, val url: String)

/** Checks GitHub Releases, which is how the app is distributed. */
class UpdateChecker(private val http: OkHttpClient = OkHttpClient()) {
    suspend fun check(): Result<AvailableUpdate?> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()
            http.newCall(request).execute().use { response ->
                if (response.code == 404) return@runCatching null
                check(response.isSuccessful) { "GitHub ${response.code}" }
                val release = Json.parseToJsonElement(response.body!!.string()).jsonObject
                val tag = release["tag_name"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                val url = release["html_url"]?.jsonPrimitive?.contentOrNull ?: return@runCatching null
                AvailableUpdate(tag.removePrefix("v"), url).takeIf { isNewer(it.version, BuildConfig.VERSION_NAME) }
            }
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val diff = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (diff != 0) return diff > 0
        }
        return false
    }
}
