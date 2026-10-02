package com.sysdrill.backend.learning

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/** PLAN.md Round E28 — L9 diagnostic puzzles (no login) and the C12 weekly puzzle. */
@SpringBootTest
@AutoConfigureMockMvc
class DiagnosticPuzzleTest(
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    @Test
    fun `a seed always gives the same metrics-only puzzle, graded against its domain, without logging in`() {
        val first = mockMvc.perform(get("/learning/puzzles?seed=42")).andExpect(status().isOk).andReturn().response.contentAsString
        val again = mockMvc.perform(get("/learning/puzzles?seed=42")).andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(again).isEqualTo(first)
        assertThat(JsonPath.read<List<String>>(first, "$.patterns[*].key")).hasSize(7)
        assertThat(JsonPath.read<List<Any>>(first, "$.points")).hasSizeGreaterThan(5)
        assertThat(first.substringBefore("\"patterns\"")).doesNotContain("coupon", "notification", "payment") // the metrics give nothing away by name

        val domain = DiagnosticPuzzles.domainOf(42)
        val right = mockMvc.perform(
            post("/learning/puzzles/42/answer").contentType(MediaType.APPLICATION_JSON).content("""{"pattern":"$domain","check":"pods"}""")
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Boolean>(right, "$.patternCorrect")).isTrue()
        assertThat(JsonPath.read<String>(right, "$.answerPattern")).isEqualTo(domain)
        mockMvc.perform(post("/learning/puzzles/42/answer").contentType(MediaType.APPLICATION_JSON).content("""{"check":"vibes"}"""))
            .andExpect(status().isBadRequest)

        // Seeds spread over every domain — the puzzle isn't stuck on one pattern.
        assertThat((1L..60L).map { DiagnosticPuzzles.domainOf(it) }.toSet()).hasSize(7)
    }

    @Test
    fun `the weekly puzzle hides the split until I answer, once`() {
        fun user() = userRepository.save(User(email = "wwyd-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "wwyd")).id!!
        val a = user()
        val b = user()
        val before = mockMvc.perform(get("/community/wwyd").header("Authorization", bearerHeader(a))).andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Any?>(before, "$.distribution")).isNull()
        assertThat(JsonPath.read<Any?>(before, "$.result")).isNull()

        fun answer(u: UUID, body: String) = mockMvc.perform(
            post("/community/wwyd").contentType(MediaType.APPLICATION_JSON).header("Authorization", bearerHeader(u)).content(body)
        )
        answer(b, """{"choice":"cache","reason":"적중률부터 봅니다","reasonPublic":true}""").andExpect(status().isOk)
        val after = answer(a, """{"choice":"db-pool","reason":"비공개 이유"}""").andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<String>(after, "$.myChoice")).isEqualTo("db-pool")
        assertThat(JsonPath.read<Int>(after, "$.distribution.cache")).isGreaterThanOrEqualTo(1)
        assertThat(JsonPath.read<List<String>>(after, "$.reasons[*].reason")).contains("적중률부터 봅니다").doesNotContain("비공개 이유")
        answer(a, """{"choice":"cache"}""").andExpect(status().isConflict)
    }
}
