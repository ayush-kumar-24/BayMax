package com.ayush.baymax.llm

/** Provider-neutral request and reply. Every provider sits behind [LlmClient] (NFR-16). */

data class ChatTurn(val role: Role, val text: String) {
    enum class Role { User, Assistant }
}

data class ToolParam(val name: String, val type: Type, val description: String, val required: Boolean = true) {
    enum class Type { String, Integer }
}

data class ToolSpec(val name: String, val description: String, val params: List<ToolParam>)

data class ToolCall(val name: String, val args: Map<String, String>)

data class LlmRequest(
    val system: String,
    val turns: List<ChatTurn>,
    val tools: List<ToolSpec>,
)

data class LlmReply(val text: String, val toolCalls: List<ToolCall>)

class LlmException(message: String, val retryable: Boolean, cause: Throwable? = null) : Exception(message, cause)

interface LlmClient {
    val name: String
    /** True when the client has what it needs (key, Firebase config) to be tried at all. */
    val isConfigured: Boolean
    suspend fun generate(request: LlmRequest): LlmReply
}

/**
 * FR-28: on error or rate limit, retry the same provider once, then move to the next one.
 * Returns null when every provider failed (FR-29), never throws.
 */
class LlmRouter(private val clients: List<LlmClient>) {

    /** Name of the provider that produced the last reply, for the status line and logs. */
    var lastProvider: String? = null
        private set

    suspend fun generate(request: LlmRequest): LlmReply? {
        for (client in clients.filter { it.isConfigured }) {
            var attempt = 0
            while (attempt < 2) {
                try {
                    val reply = client.generate(request)
                    lastProvider = client.name
                    return reply
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: LlmException) {
                    if (!e.retryable) break
                } catch (e: Exception) {
                    // Network or parsing problem: treat as retryable.
                }
                attempt++
            }
        }
        lastProvider = null
        return null
    }
}
