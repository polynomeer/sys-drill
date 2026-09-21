package com.sysdrill.backend.evaluation.llm

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.jupiter.api.Test

class AnthropicLlmClientTest {

    private val client = AnthropicLlmClient(
        baseUrl = "https://api.anthropic.com",
        apiKey = "", // offline mode
        model = "claude-sonnet-5",
        maxTokens = 2000,
    )

    @Test
    fun `returns an offline placeholder when no API key is configured`() {
        val result = client.complete(systemPrompt = "system", userPrompt = "user prompt")

        assertThat(result.model).isEqualTo("offline-fallback")
        assertThat(result.text).contains("totalScore")
    }

    @Test
    fun `still honors the force-failure marker in offline mode`() {
        assertThatIllegalStateException()
            .isThrownBy {
                client.complete(systemPrompt = "system", userPrompt = "please ${AnthropicLlmClient.FORCE_FAILURE_MARKER}")
            }
    }
}

/**
 * Real-HTTP tests against a throwaway in-process server (no mocking framework
 * in this codebase — same DI-substitution philosophy as FakeGoogleOAuthClient,
 * applied one layer down at the socket). Each test hands the client a canned
 * Messages-API response body and checks how the client interprets it.
 */
class AnthropicLlmClientResponseHandlingTest {

    private fun clientAgainst(responseBody: String, maxTokens: Int = 2000): Pair<AnthropicLlmClient, AutoCloseable> {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/messages") { exchange ->
            val bytes = responseBody.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        val client = AnthropicLlmClient(
            baseUrl = "http://127.0.0.1:${server.address.port}",
            apiKey = "test-key",
            model = "claude-sonnet-5",
            maxTokens = maxTokens,
        )
        return client to AutoCloseable { server.stop(0) }
    }

    @Test
    fun `returns the text block and usage on a normal end_turn response`() {
        val (client, server) = clientAgainst(
            """{"id":"msg_1","content":[{"type":"thinking","text":""},{"type":"text","text":"{\"totalScore\":78}"}],
               "stop_reason":"end_turn","usage":{"input_tokens":1928,"output_tokens":3638,"output_tokens_details":{"thinking_tokens":1699}}}""",
        )
        server.use {
            val result = client.complete(systemPrompt = "system", userPrompt = "user")
            assertThat(result.text).isEqualTo("""{"totalScore":78}""")
            assertThat(result.inputTokens).isEqualTo(1928)
            assertThat(result.outputTokens).isEqualTo(3638)
        }
    }

    @Test
    fun `fails with an actionable message when the response was truncated at max_tokens`() {
        // Exactly what claude-sonnet-5 returned at max_tokens=2000 on a real
        // design_evaluation prompt (2026-09-21): the whole budget went to
        // thinking, so there is no text block at all. Before this check the
        // failure surfaced as the misleading "No text content block".
        val (client, server) = clientAgainst(
            """{"id":"msg_2","content":[{"type":"thinking","text":""}],
               "stop_reason":"max_tokens","usage":{"input_tokens":1928,"output_tokens":2000,"output_tokens_details":{"thinking_tokens":2000}}}""",
            maxTokens = 2000,
        )
        server.use {
            assertThatIllegalStateException()
                .isThrownBy { client.complete(systemPrompt = "system", userPrompt = "user") }
                .withMessageContaining("max_tokens=2000")
                .withMessageContaining("thinking_tokens=2000")
                .withMessageContaining("LLM_ANTHROPIC_MAX_TOKENS")
        }
    }
}
