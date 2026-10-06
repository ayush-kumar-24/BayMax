package com.ayush.baymax.llm

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FunctionDeclaration
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.Tool
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Primary LLM: Gemini Developer API through Firebase AI Logic (SRS 4.3). No API key ships
 * in the app; Firebase manages it. Only used when the build includes google-services.json.
 */
class FirebaseGeminiClient(
    private val context: Context,
    private val model: String = GEMINI_MODEL,
) : LlmClient {
    override val name = "Gemini"

    override val isConfigured: Boolean
        get() = runCatching { FirebaseApp.getApps(context).isNotEmpty() }.getOrDefault(false)

    private fun declarations(tools: List<ToolSpec>) = tools.map { spec ->
        FunctionDeclaration(
            name = spec.name,
            description = spec.description,
            parameters = spec.params.associate { p ->
                p.name to when (p.type) {
                    ToolParam.Type.String -> Schema.string(p.description)
                    ToolParam.Type.Integer -> Schema.integer(p.description)
                }
            },
            optionalParameters = spec.params.filterNot { it.required }.map { it.name },
        )
    }

    override suspend fun generate(request: LlmRequest): LlmReply {
        try {
            val generative = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = model,
                generationConfig = generationConfig {
                    temperature = 0.4f
                    maxOutputTokens = 300
                },
                tools = if (request.tools.isEmpty()) null else listOf(Tool.functionDeclarations(declarations(request.tools))),
                systemInstruction = content { text(request.system) },
            )
            val contents = request.turns.map { t ->
                content(role = if (t.role == ChatTurn.Role.User) "user" else "model") { text(t.text) }
            }
            val response = generative.generateContent(*contents.toTypedArray())
            val calls = response.functionCalls.map { call ->
                ToolCall(call.name, call.args.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.contentOrNull?.let { k to it } }.toMap())
            }
            return LlmReply(response.text.orEmpty(), calls)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Quota (429), server and network errors all deserve the one retry of FR-28.
            throw LlmException("Firebase Gemini: ${e.message}", retryable = true, cause = e)
        }
    }
}
