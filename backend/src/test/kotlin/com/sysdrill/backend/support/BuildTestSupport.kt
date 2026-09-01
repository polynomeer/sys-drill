package com.sysdrill.backend.support

import com.jayway.jsonpath.JsonPath
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.ObjectMapper
import java.util.UUID

/**
 * PLAN.md step 31 — `POST /build-challenges/{slug}/submissions` no longer
 * takes `userId` in the body (derived from the Bearer token instead, see
 * BuildController). Mirrors [startSession]'s "single point of change" shape
 * so the 6 build-challenge test files that used to build the request body
 * by hand can share one helper instead of each carrying its own copy.
 */
fun MockMvc.submitBuildChallenge(
    objectMapper: ObjectMapper,
    slug: String,
    userId: UUID,
    sourceCode: String,
    commitRef: String? = "test",
): UUID {
    val body = objectMapper.writeValueAsString(mapOf("sourceCode" to sourceCode, "commitRef" to commitRef))
    val response = perform(
        post("/build-challenges/$slug/submissions")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", bearerHeader(userId))
            .content(body)
    )
        .andExpect(status().isCreated)
        .andReturn().response.contentAsString
    return UUID.fromString(JsonPath.read(response, "$.id"))
}
