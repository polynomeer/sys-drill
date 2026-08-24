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
