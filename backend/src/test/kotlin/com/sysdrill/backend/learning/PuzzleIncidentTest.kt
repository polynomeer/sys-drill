package com.sysdrill.backend.learning

import com.jayway.jsonpath.JsonPath
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

/**
 * docs/LEARNING_DEEPENING_PLAN.md L16 (PLAN.md Round E40) — the puzzle as a short incident response.
 * For every domain, over many seeds (each shakes capacities and the moment differently): the failure
 * pattern's mitigation recovers and its Bad Fix doesn't; evidence and curves are the same for the same seed.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PuzzleIncidentTest(
    @Autowired val incident: PuzzleIncidentService,
    @Autowired val mockMvc: MockMvc,
) {
    /** The first [n] seeds whose incident is each domain. */
    private fun seedsByDomain(n: Int): Map<String, List<Long>> =
        generateSequence(1L) { it + 1 }.take(5000).groupBy { DiagnosticPuzzles.domainOf(it) }.mapValues { it.value.take(n) }

    @Test
    fun `in every domain the pattern's mitigation recovers and its Bad Fix does not`() {
        val seeds = seedsByDomain(30)
        assertThat(seeds.keys).containsExactlyInAnyOrderElementsOf(DiagnosticPuzzles.CURRENT.domains)
        val problems = seeds.flatMap { (domain, list) ->
            list.flatMap { seed ->
                val good = incident.act(seed, PuzzleActRequest(PuzzleIncidentService.DOMAIN_ACTIONS.getValue(domain).let { all ->
                    incident.act(seed, PuzzleActRequest(listOf(all.first()))).recommended
                }))
                val bad = incident.act(seed, PuzzleActRequest(good.badFix))
                listOfNotNull(
                    if (good.verdict == "RECOVERED") null else "$domain seed $seed: mitigation ${good.recommended} → ${good.verdict}",
                    if (bad.verdict != "RECOVERED") null else "$domain seed $seed: Bad Fix ${good.badFix} recovered",
                    if (bad.badFixWhy.isNullOrBlank()) "$domain seed $seed: doing exactly the Bad Fix should explain why" else null,
                )
            }
        }
        assertThat(problems).isEmpty()
    }

    @Test
    fun `every investigation opens deterministic evidence for every domain`() {
        val problems = seedsByDomain(3).flatMap { (domain, list) ->
            list.flatMap { seed ->
                PuzzleIncidentService.INVESTIGATIONS.mapNotNull { inv ->
                    val first = incident.evidence(seed, inv.key)
                    val again = incident.evidence(seed, inv.key)
                    val empty = when (inv.key) {
                        "logs" -> first.logs.isNullOrEmpty()
                        "trace" -> first.spans.isNullOrEmpty()
                        "components" -> first.state == null || first.domain != domain
                        "changes" -> first.changes.isNullOrEmpty()
                        else -> true
                    }
                    when {
                        empty -> "$domain seed $seed: ${inv.key} is empty"
                        first != again -> "$domain seed $seed: ${inv.key} differs between calls"
                        else -> null
                    }
                }
            }
        }
        assertThat(problems).isEmpty()
    }

    @Test
    fun `the debrief names the alert that fired first and the domain's actions`() {
        val problems = seedsByDomain(5).flatMap { (domain, list) ->
            list.mapNotNull { seed ->
                val d = incident.debrief(seed)
                when {
                    d.signals.firstOrNull()?.second == null -> "$domain seed $seed: no alert fired before the moment asked about"
                    d.actions.size != 3 -> "$domain seed $seed: ${d.actions.size} actions"
                    d.relatedConcepts.isEmpty() -> "$domain seed $seed: no related concepts"
                    d.lab == null -> "$domain seed $seed: no lab"
                    else -> null
                }
            }
        }
        assertThat(problems).isEmpty()
    }

    @Test
    fun `the puzzle API offers investigations, picks a domain and rejects actions from another system`() {
        val body = mockMvc.perform(get("/learning/puzzles?domain=payment")).andExpect(status().isOk).andReturn().response.contentAsString
        val seed = JsonPath.read<Number>(body, "$.seed").toLong()
        assertThat(DiagnosticPuzzles.domainOf(seed)).isEqualTo("payment")
        assertThat(JsonPath.read<List<String>>(body, "$.investigations[*].key")).containsExactly("changes", "logs", "trace", "components")

        mockMvc.perform(get("/learning/puzzles/$seed/evidence/logs")).andExpect(status().isOk)
        mockMvc.perform(get("/learning/puzzles/$seed/evidence/vibes")).andExpect(status().isBadRequest)
        mockMvc.perform(post("/learning/puzzles/$seed/act").contentType(MediaType.APPLICATION_JSON).content("""{"actions":["ROLLBACK"]}"""))
            .andExpect(status().isBadRequest)
        val act = mockMvc.perform(
            post("/learning/puzzles/$seed/act").contentType(MediaType.APPLICATION_JSON)
                .content("""{"actions":["ENABLE_IDEMPOTENT_PG_RETRY","ISOLATE_PAYMENT_POOL"]}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(act, "$.timeline.resolved.series[*].label")).containsExactly("아무것도 하지 않으면", "내 조치", "장애 패턴의 완화 순서")
        mockMvc.perform(get("/learning/puzzles?domain=nowhere")).andExpect(status().isBadRequest)
    }
}
