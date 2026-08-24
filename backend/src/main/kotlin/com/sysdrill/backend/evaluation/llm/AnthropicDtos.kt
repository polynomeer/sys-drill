package com.sysdrill.backend.evaluation.llm

// Field names deliberately match the Anthropic Messages API's JSON exactly
// (snake_case) instead of using @JsonProperty, so (de)serialization works
// under Spring Boot 4's default Jackson 3 message converters with no extra
// annotation-package guessing.

data class AnthropicMessage(val role: String, val content: String)

data class AnthropicRequest(
    val model: String,
    val max_tokens: Int,
    val system: String,
    val messages: List<AnthropicMessage>,
)

data class AnthropicContentBlock(val type: String? = null, val text: String? = null)

data class AnthropicUsage(val input_tokens: Int = 0, val output_tokens: Int = 0)

data class AnthropicResponse(
    val id: String? = null,
    val content: List<AnthropicContentBlock> = emptyList(),
    val stop_reason: String? = null,
    val usage: AnthropicUsage? = null,
)
