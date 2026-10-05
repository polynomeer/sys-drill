package com.sysdrill.backend.learning

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.build.BuildChallengeRepository
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
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
 * docs/LEARNING_DEEPENING_PLAN.md L14 (PLAN.md Round E35, ADR-0054) — every seeded design guide
 * stays true to the engine and to the rest of the content: each step's claims match the engine,
 * steps chain (a step starts from the previous design plus its change), and every concept, lab,
 * Build challenge, failure pattern and checklist anchor a guide points at exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DesignGuideTest(
    @Autowired val guides: DesignGuideRepository,
    @Autowired val service: DesignGuideService,
    @Autowired val blocks: ContentBlockService,
    @Autowired val concepts: LearningConceptRepository,
    @Autowired val labs: LearningLabRepository,
    @Autowired val challenges: BuildChallengeRepository,
    @Autowired val failurePatterns: FailurePatternRepository,
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    @Test
    fun `every guide's claims match the engine and its steps chain`() {
        val problems = guides.findAll().flatMap { guide ->
            val steps = service.steps(guide)
            val claimProblems = steps.flatMapIndexed { i, step ->
                blocks.brokenClaims(service.numbers(guide.domain, step)).map { "${guide.domain} step ${i + 1}: $it" } +
                    (if (step.claims.isEmpty()) listOf("${guide.domain} step ${i + 1}: no claims — the result says nothing checkable") else emptyList())
            }
            val chainProblems = steps.zipWithNext().mapIndexedNotNull { i, (prev, next) ->
                val expected = prev.traits + prev.action.change
                if (next.traits == expected) null else "${guide.domain} step ${i + 2} starts from ${next.traits}, expected step ${i + 1}'s design ${expected}"
            }
            val actionProblems = steps.mapIndexedNotNull { i, step ->
                if (step.action.kind in setOf("TUNE", "REDESIGN")) null else "${guide.domain} step ${i + 1}: action kind ${step.action.kind}"
            }
            // Extra numbers blocks inside the requirements, architecture or a step carry claims of their own.
            val blockProblems = runCatching {
                val sections = listOf("requirements" to guide.requirements, "architecture" to guide.architecture) +
                    steps.mapIndexed { i, step -> "step ${i + 1}" to step.blocks }
                sections.flatMap { (where, raw) ->
                    blocks.resolve(raw).filterIsInstance<NumbersBlock>().flatMap { blocks.brokenClaims(it) }
                        .map { "${guide.domain} $where numbers block: $it" }
                }
            }.getOrElse { listOf("${guide.domain}: ${it.message}") }
            claimProblems + chainProblems + actionProblems + blockProblems
        }
        assertThat(problems).describedAs("design guides out of step with the engine").isEmpty()
    }

    @Test
    fun `everything a guide links to exists`() {
        val conceptKeys = concepts.findAll().map { it.riskKey }.toSet()
        val labSlugs = labs.findAll().map { it.slug }.toSet()
        val challengeSlugs = challenges.findAll().map { it.slug }.toSet()
        val patternDomains = failurePatterns.findAll().map { it.domain }.toSet()
        val problems = guides.findAll().flatMap { guide ->
            val steps = service.steps(guide)
            val anchors = setOf("requirements", "architecture") + steps.indices.map { "step-${it + 1}" }
            steps.flatMapIndexed { i, step ->
                val at = "${guide.domain} step ${i + 1}"
                step.concepts.filterNot { it in conceptKeys }.map { "$at: unknown concept $it" } +
                    step.links.labs.filterNot { it in labSlugs }.map { "$at: unknown lab $it" } +
                    step.links.challenges.filterNot { it in challengeSlugs }.map { "$at: unknown Build challenge $it" } +
                    listOfNotNull(step.links.failurePattern?.takeIf { it !in patternDomains }?.let { "$at: unknown failure pattern $it" })
            } + guide.checklist.filterNot { it["where"] in anchors }.map { "${guide.domain}: checklist '${it["item"]}' points at ${it["where"]}" }
        }
        assertThat(problems).describedAs("broken links in design guides").isEmpty()
    }

    @Test
    fun `the guide API returns each step with the engine's before and after`() {
        val user = userRepository.save(User(email = "guide-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "guide")).id!!
        val list = mockMvc.perform(get("/learning/guides").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(list, "$[*].domain")).containsExactly(
            "coupon", "notification", "product-browsing", "payment", "reservation", "batch-settlement", "autoscaling", "deployment",
        )

        val body = mockMvc.perform(get("/learning/guides/product-browsing").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        // Step 1: the incident with nothing changed — 40× DB read utilisation; single-flight removes the ×10 dogpile.
        assertThat(JsonPath.read<Double>(body, "$.steps[0].metrics.dbReadLoad.before")).isCloseTo(40.0, Offset.offset(0.001))
        assertThat(JsonPath.read<Double>(body, "$.steps[0].metrics.dbReadLoad.after")).isCloseTo(4.0, Offset.offset(0.001))
        // Step 3: policy split brings the hit ratio back to 0.8 and P95 to the uncontended 60ms.
        assertThat(JsonPath.read<Double>(body, "$.steps[2].after.cacheHitRatio")).isCloseTo(0.8, Offset.offset(0.001))
        assertThat(JsonPath.read<Double>(body, "$.steps[2].after.p95LatencyMs")).isCloseTo(60.0, Offset.offset(0.001))
        assertThat(JsonPath.read<List<String>>(body, "$.steps[*].concepts[*].label")).isNotEmpty
        assertThat(JsonPath.read<List<String>>(body, "$.pitfalls[*].fix")).isNotEmpty
        assertThat(JsonPath.read<Map<String, Any>>(body, "$.steps[1].traitsAfter")).containsEntry("readReplicaCount", 1)
    }
}
