package com.sysdrill.backend.learning

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.data.Offset
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.util.UUID

/**
 * docs/LEARNING_DEEPENING_PLAN.md L12 (PLAN.md Round E34) — the seeded content blocks stay true to
 * the engine. Every block must parse, every number block must resolve (known domain, traits and
 * metrics), and every claim a block's prose makes must match what the engine computes now. An
 * engine change that turns a sentence false fails here, not silently on the page.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ContentBlocksTest(
    @Autowired val service: ContentBlockService,
    @Autowired val conceptRepository: LearningConceptRepository,
    @Autowired val failurePatternRepository: FailurePatternRepository,
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private fun allSeeded(): Map<String, List<Map<String, Any?>>> =
        conceptRepository.findAll().associate { "concept ${it.riskKey}" to it.blocks } +
            failurePatternRepository.findAll().associate { "failure pattern ${it.domain}" to it.blocks }

    @Test
    fun `every seeded block resolves and every claim matches the engine`() {
        val problems = allSeeded().flatMap { (owner, raw) ->
            val resolved = runCatching { service.resolve(raw) }.getOrElse { return@flatMap listOf("$owner: ${it.message}") }
            resolved.flatMapIndexed { i, block ->
                when (block) {
                    is NumbersBlock -> service.brokenClaims(block).map { "$owner block $i: $it" } +
                        (if (block.claims.isEmpty()) listOf("$owner block $i: a numbers block with no claims says nothing checkable") else emptyList())
                    is DiagramBlock -> if (block.mermaid.isBlank() || block.alt.isBlank()) listOf("$owner block $i: diagram needs mermaid and alt text") else emptyList()
                    is CompareBlock -> listOf(block.before, block.after)
                        .filter { it.mermaid.isBlank() || it.alt.isBlank() }
                        .map { "$owner block $i: compare pane '${it.label}' needs mermaid and alt text" }
                    is TimelineBlock -> service.brokenClaims(block).map { "$owner block $i: $it" } +
                        block.scenarios.filter { it.claims.isEmpty() || it.tone !in setOf("bad", "good") }
                            .map { "$owner block $i: scenario '${it.label}' needs claims and a bad/good tone" } +
                        block.resolved!!.firstAlerts.filter { it.second == null }.map { "$owner block $i: alert '${it.label}' never fires" }
                    is CalloutBlock -> if (block.tone !in setOf("tip", "warning", "tradeoff")) listOf("$owner block $i: unknown callout tone ${block.tone}") else emptyList()
                    else -> emptyList()
                }
            }
        }
        assertThat(problems).describedAs("content blocks out of step with the engine or malformed").isEmpty()
    }

    @Test
    fun `single-flight carries one block of every kind`() {
        val kinds = service.parse(conceptRepository.findById("MISSING_SINGLE_FLIGHT").orElseThrow().blocks).map { it::class.simpleName }.toSet()
        assertThat(kinds).containsExactlyInAnyOrder(
            "TextBlock", "StepsBlock", "CalloutBlock", "CompareBlock", "NumbersBlock", "SystemBlock",
        )
    }

    @Test
    fun `the caching concepts carry the L13 shape`() {
        // docs/LEARNING_DEEPENING_PLAN.md L13 pilot (Round E37): a mechanism picture, the steps, and a callout on the cost.
        val caching = conceptRepository.findAll().filter { it.category == "CACHING_DATA_ACCESS" }
        assertThat(caching).hasSize(4)
        val missing = caching.flatMap { concept ->
            val kinds = service.parse(concept.blocks).map { it::class.simpleName }.toSet()
            listOf("CompareBlock", "StepsBlock", "CalloutBlock").filterNot { it in kinds }.map { "${concept.riskKey} has no $it" }
        }
        assertThat(missing).isEmpty()
    }

    @Test
    fun `the stampede timeline fires the effect's alerts before the cause's`() {
        val timeline = service.resolve(failurePatternRepository.findById("product-browsing").orElseThrow().blocks)
            .filterIsInstance<TimelineBlock>().single()
        val resolved = timeline.resolved!!
        // Hit ratio falls 0.9 → 0.2 over the 90 s ramp while ×10 dogpiled misses take DB reads from 2.5% to 4000%:
        // the DB alert fires within seconds, the hit-ratio one (the cause) last.
        assertThat(resolved.firstAlerts.map { it.metric }).containsExactly("dbReadLoad", "errorRate", "p95LatencyMs", "cacheHitRatio")
        assertThat(resolved.firstAlerts.first().second).isEqualTo(2L)
        assertThat(resolved.seconds.first()).isEqualTo(-30L)
        assertThat(resolved.series.map { it.tone }).containsExactly("none", "bad", "good")
        // The mitigation ends at the guide's final state: 50% DB read, P95 back to 60 ms; the replica-only fix leaves P95 at 480.
        assertThat(resolved.series[2].values.getValue("p95LatencyMs").last()).isCloseTo(60.0, Offset.offset(0.001))
        assertThat(resolved.series[1].values.getValue("p95LatencyMs").last()).isCloseTo(480.0, Offset.offset(0.001))
        assertThat(resolved.series[2].status.last()).isEqualTo("RECOVERED")
    }

    @Test
    fun `a block naming an unknown trait, metric or domain is rejected`() {
        fun numbers(domain: String, change: Map<String, Any>, metric: String) = listOf(
            mapOf("type" to "numbers", "domain" to domain, "change" to change, "changeLabel" to "x", "metrics" to listOf(metric)),
        )
        assertThatThrownBy { service.resolve(numbers("product-browsing", mapOf("noSuchTrait" to 1), "dbReadLoad")) }.hasMessageContaining("noSuchTrait")
        assertThatThrownBy { service.resolve(numbers("product-browsing", mapOf("readReplicaCount" to 1), "noSuchMetric")) }.hasMessageContaining("noSuchMetric")
        assertThatThrownBy { service.resolve(numbers("no-such-domain", mapOf("readReplicaCount" to 1), "dbReadLoad")) }.hasMessageContaining("no-such-domain")
    }

    @Test
    fun `the concept API returns blocks with the engine's values filled in`() {
        val user = userRepository.save(User(email = "blocks-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "blocks")).id!!
        val body = mockMvc.perform(get("/learning/concepts/MISSING_SINGLE_FLIGHT").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString

        val types: List<String> = JsonPath.read(body, "$.blocks[*].type")
        assertThat(types).contains("compare", "numbers", "system")
        // Product-browsing incident: 10,000 rps, hit 0.2 → 8,000 misses; ×10 dogpile without single-flight
        // over 2,000 rps of DB read capacity = 40×; with it, 4× — still saturated (see the block's claims).
        val before = JsonPath.read<List<Double>>(body, "$.blocks[?(@.type == 'numbers')].resolved.dbReadLoad.before").first()
        val after = JsonPath.read<List<Double>>(body, "$.blocks[?(@.type == 'numbers')].resolved.dbReadLoad.after").first()
        assertThat(before).isCloseTo(40.0, Offset.offset(0.001))
        assertThat(after).isCloseTo(4.0, Offset.offset(0.001))
        val systemHit = JsonPath.read<List<Double>>(body, "$.blocks[?(@.type == 'system')].state.cacheHitRatio").single()
        assertThat(systemHit).isCloseTo(0.2, Offset.offset(0.001))
    }
}
