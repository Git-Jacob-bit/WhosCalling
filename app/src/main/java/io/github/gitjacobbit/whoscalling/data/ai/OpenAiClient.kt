package io.github.gitjacobbit.whoscalling.data.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

@Serializable
data class AiVerdict(
    val verdict: String,
    val score: Int,
    val category: String,
    val owner: String?,
    val summary: String,
    val sources: List<String>,
)

class OpenAiException(message: String) : IOException(message)

class OpenAiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Cheap call used by onboarding/settings to validate a key. */
    suspend fun testKey(apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .header("Authorization", "Bearer $apiKey")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw OpenAiException(errorMessage(response.code, response.body?.string()))
            }
        }
    }

    suspend fun investigate(apiKey: String, model: String, e164: String, localContext: String): AiVerdict =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", model)
                put("instructions", SYSTEM_PROMPT)
                put("input", "Numer: $e164\nDane lokalne: $localContext")
                // The phone rings for ~30 s, so trade some depth for speed on reasoning models.
                if (model.startsWith("gpt-5") || model.startsWith("o")) {
                    putJsonObject("reasoning") { put("effort", "low") }
                }
                putJsonArray("tools") {
                    add(buildJsonObject {
                        put("type", "web_search")
                        putJsonObject("user_location") {
                            put("type", "approximate")
                            put("country", "PL")
                        }
                    })
                }
                putJsonObject("text") { put("format", VERDICT_FORMAT) }
            }
            val request = Request.Builder()
                .url("$BASE_URL/responses")
                .header("Authorization", "Bearer $apiKey")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()

            http.newCall(request).execute().use { response ->
                val raw = response.body?.string()
                if (!response.isSuccessful) throw OpenAiException(errorMessage(response.code, raw))
                json.decodeFromString(AiVerdict.serializer(), extractOutputText(raw ?: ""))
            }
        }

    private fun extractOutputText(raw: String): String {
        val output = json.parseToJsonElement(raw).jsonObject["output"]?.jsonArray
            ?: throw OpenAiException("Brak pola output w odpowiedzi")
        return output.asSequence()
            .map { it.jsonObject }
            .filter { it["type"]?.jsonPrimitive?.contentOrNull == "message" }
            .flatMap { it["content"]?.jsonArray.orEmpty().asSequence() }
            .map { it.jsonObject }
            .firstOrNull { it["type"]?.jsonPrimitive?.contentOrNull == "output_text" }
            ?.get("text")?.jsonPrimitive?.contentOrNull
            ?: throw OpenAiException("Model nie zwrócił odpowiedzi tekstowej")
    }

    private fun errorMessage(code: Int, raw: String?): String {
        val apiMessage = raw?.let {
            runCatching {
                json.parseToJsonElement(it).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            }.getOrNull()
        }
        return when (code) {
            401 -> "Nieprawidłowy klucz API"
            429 -> "Limit zapytań lub brak środków na koncie OpenAI"
            else -> apiMessage ?: "Błąd OpenAI ($code)"
        }
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    companion object {
        private const val BASE_URL = "https://api.openai.com/v1"

        private val SYSTEM_PROMPT = """
            Jesteś asystentem identyfikującym nieznane numery telefonów dzwoniące do użytkownika w Polsce.
            Użyj wyszukiwarki, aby znaleźć informacje o numerze: serwisy z opiniami o numerach
            (np. nieznany-numer.pl, ktozadzwonil.pl, tellows), strony firm, rejestry, fora.
            Szukaj numeru w różnych formatach (z +48 i bez, ze spacjami i bez).
            Zasady:
            - Nie zgaduj. Jeśli nie ma wiarygodnych informacji, użyj verdict "unknown" i category "unknown".
            - Pojedyncza niepotwierdzona opinia to za mało na "reject".
            - score to ryzyko w skali 0-100 (0 = bezpieczny, 100 = na pewno spam/oszustwo).
            - verdict: "answer" (warto odebrać), "caution" (ostrożnie), "reject" (nie odbieraj), "unknown".
            - owner: nazwa firmy/instytucji, jeśli jest pewna, inaczej null.
            - summary: maksymalnie 2 krótkie zdania po polsku, konkretnie (kto dzwoni, w jakim celu, ile zgłoszeń).
            - sources: adresy URL, na których oparłeś ocenę (maks. 5).
        """.trimIndent()

        private val VERDICT_FORMAT: JsonObject = buildJsonObject {
            put("type", "json_schema")
            put("name", "caller_verdict")
            put("strict", true)
            putJsonObject("schema") {
                put("type", "object")
                put("additionalProperties", false)
                putJsonArray("required") {
                    listOf("verdict", "score", "category", "owner", "summary", "sources").forEach { add(it) }
                }
                putJsonObject("properties") {
                    putJsonObject("verdict") {
                        put("type", "string")
                        putJsonArray("enum") { listOf("answer", "caution", "reject", "unknown").forEach { add(it) } }
                    }
                    putJsonObject("score") { put("type", "integer") }
                    putJsonObject("category") {
                        put("type", "string")
                        putJsonArray("enum") {
                            listOf("telemarketing", "scam", "courier", "bank", "company", "institution", "private", "unknown")
                                .forEach { add(it) }
                        }
                    }
                    putJsonObject("owner") {
                        putJsonArray("type") { add("string"); add("null") }
                    }
                    putJsonObject("summary") { put("type", "string") }
                    putJsonObject("sources") {
                        put("type", "array")
                        putJsonObject("items") { put("type", "string") }
                    }
                }
            }
        }
    }
}
