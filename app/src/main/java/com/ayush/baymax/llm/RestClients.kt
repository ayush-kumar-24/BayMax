package com.ayush.baymax.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL

/** Minimal HTTPS POST so clients can be tested with a fake. TLS comes from the platform (SRS 4.4). */
fun interface HttpTransport {
    /** Returns (status code, body). Throws on connection failure. */
    suspend fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String>
}

object UrlConnectionTransport : HttpTransport {
    override suspend fun post(url: String, headers: Map<String, String>, body: String): Pair<Int, String> =
        withContext(Dispatchers.IO) {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 25_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                conn.outputStream.use { it.write(body.toByteArray()) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
            } finally {
                conn.disconnect()
            }
        }
}

private val json = Json { ignoreUnknownKeys = true }

private fun statusError(provider: String, code: Int, body: String) = LlmException(
    "$provider HTTP $code: ${body.take(200)}",
    // Rate limits and server errors are worth one retry; auth and bad requests are not.
    retryable = code == 429 || code >= 500 || code == 408,
)

private fun JsonElement.str(): String? = (this as? JsonPrimitive)?.contentOrNull

/** Turns a JSON object of arguments into plain strings (numbers stay as their text). */
private fun argsOf(obj: JsonObject?): Map<String, String> =
    obj?.mapNotNull { (k, v) -> v.str()?.let { k to it } }?.toMap().orEmpty()

private fun ToolParam.Type.jsonType(upper: Boolean) = when (this) {
    ToolParam.Type.String -> if (upper) "STRING" else "string"
    ToolParam.Type.Integer -> if (upper) "INTEGER" else "integer"
}

/**
 * Groq, OpenAI-compatible chat completions (fallback LLM, SRS 4.3).
 * Key comes from secrets.properties via BuildConfig, never from source control (NFR-7).
 */
class GroqClient(
    private val apiKey: String,
    private val model: String = "openai/gpt-oss-120b",
    private val transport: HttpTransport = UrlConnectionTransport,
) : LlmClient {
    override val name = "Groq"
    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    fun body(request: LlmRequest): String = buildJsonObject {
        put("model", model)
        put("temperature", 0.4)
        put("max_tokens", 300)
        putJsonArray("messages") {
            addJsonObject { put("role", "system"); put("content", request.system) }
            request.turns.forEach { t ->
                addJsonObject {
                    put("role", if (t.role == ChatTurn.Role.User) "user" else "assistant")
                    put("content", t.text)
                }
            }
        }
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                request.tools.forEach { tool ->
                    addJsonObject {
                        put("type", "function")
                        putJsonObject("function") {
                            put("name", tool.name)
                            put("description", tool.description)
                            putJsonObject("parameters") {
                                put("type", "object")
                                putJsonObject("properties") {
                                    tool.params.forEach { p ->
                                        putJsonObject(p.name) { put("type", p.type.jsonType(false)); put("description", p.description) }
                                    }
                                }
                                putJsonArray("required") { tool.params.filter { it.required }.forEach { add(it.name) } }
                            }
                        }
                    }
                }
            }
        }
    }.toString()

    fun parse(body: String): LlmReply {
        val message = json.parseToJsonElement(body).jsonObject["choices"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("message")?.jsonObject
            ?: throw LlmException("Groq: no message", retryable = true)
        val text = message["content"]?.str().orEmpty()
        val calls = (message["tool_calls"] as? JsonArray).orEmpty().mapNotNull { c ->
            val fn = c.jsonObject["function"]?.jsonObject ?: return@mapNotNull null
            val name = fn["name"]?.str() ?: return@mapNotNull null
            val args = fn["arguments"]?.str()?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ToolCall(name, argsOf(args))
        }
        return LlmReply(text, calls)
    }

    override suspend fun generate(request: LlmRequest): LlmReply {
        val (code, response) = transport.post(
            "https://api.groq.com/openai/v1/chat/completions",
            mapOf("Authorization" to "Bearer $apiKey"),
            body(request),
        )
        if (code !in 200..299) throw statusError(name, code, response)
        return parse(response)
    }
}

/**
 * Gemini Developer API over REST, for when an AI Studio key is set instead of a Firebase
 * project. Same model and tools as [FirebaseGeminiClient].
 */
class GeminiRestClient(
    private val apiKey: String,
    private val model: String = GEMINI_MODEL,
    private val transport: HttpTransport = UrlConnectionTransport,
) : LlmClient {
    override val name = "Gemini"
    override val isConfigured: Boolean get() = apiKey.isNotBlank()

    fun body(request: LlmRequest): String = buildJsonObject {
        putJsonObject("systemInstruction") {
            putJsonArray("parts") { addJsonObject { put("text", request.system) } }
        }
        putJsonArray("contents") {
            request.turns.forEach { t ->
                addJsonObject {
                    put("role", if (t.role == ChatTurn.Role.User) "user" else "model")
                    putJsonArray("parts") { addJsonObject { put("text", t.text) } }
                }
            }
        }
        if (request.tools.isNotEmpty()) {
            putJsonArray("tools") {
                addJsonObject {
                    putJsonArray("functionDeclarations") {
                        request.tools.forEach { tool ->
                            addJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                if (tool.params.isNotEmpty()) {
                                    putJsonObject("parameters") {
                                        put("type", "OBJECT")
                                        putJsonObject("properties") {
                                            tool.params.forEach { p ->
                                                putJsonObject(p.name) { put("type", p.type.jsonType(true)); put("description", p.description) }
                                            }
                                        }
                                        putJsonArray("required") { tool.params.filter { it.required }.forEach { add(it.name) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", 0.4)
            put("maxOutputTokens", 300)
        }
    }.toString()

    fun parse(body: String): LlmReply {
        val parts = json.parseToJsonElement(body).jsonObject["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?: throw LlmException("Gemini: no candidate", retryable = true)
        val text = parts.mapNotNull { it.jsonObject["text"]?.str() }.joinToString(" ").trim()
        val calls = parts.mapNotNull { p ->
            val fc = p.jsonObject["functionCall"]?.jsonObject ?: return@mapNotNull null
            val name = fc["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ToolCall(name, argsOf(fc["args"] as? JsonObject))
        }
        return LlmReply(text, calls)
    }

    override suspend fun generate(request: LlmRequest): LlmReply {
        val (code, response) = transport.post(
            "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
            mapOf("x-goog-api-key" to apiKey),
            body(request),
        )
        if (code !in 200..299) throw statusError(name, code, response)
        return parse(response)
    }
}

/** Model from SRS 4.3. */
const val GEMINI_MODEL = "gemini-3.5-flash"
