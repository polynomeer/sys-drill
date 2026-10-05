package com.sysdrill.backend.learning

import com.jayway.jsonpath.JsonPath
import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.bearerHeader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
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

/**
 * docs/LEARNING_DEEPENING_PLAN.md L15 (PLAN.md Round E39) — lab goals are reachable with the lab's own
 * knobs and not already met at the incident's defaults; every concept the content explains with engine
 * numbers has a lab on that domain; sweep and replay come from the same engine as the Drill.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LabEnhancementTest(
    @Autowired val labs: LearningLabService,
    @Autowired val labRepository: LearningLabRepository,
    @Autowired val conceptRepository: LearningConceptRepository,
    @Autowired val contentBlocks: ContentBlockService,
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private val user: UUID by lazy {
        userRepository.save(User(email = "labs-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "labs")).id!!
    }

    private fun engineLabs() = labRepository.findAllByOrderByDisplayOrderAsc().filter { it.kind == "ENGINE" }

    /** Every combination of the lab's knobs — booleans both ways, numbers on the sweep grid. */
    private fun grid(spec: EngineSpec): List<Map<String, Any?>> =
        spec.knobs.fold(listOf(emptyMap())) { acc, knob ->
            val values: List<Any> = if (knob.type == "boolean") listOf(false, true) else labs.sweepValues(knob)
            acc.flatMap { partial -> values.map { partial + (knob.trait to it) } }
        }

    @Test
    fun `every lab goal is reachable with the lab's knobs and not met at the defaults`() {
        val problems = engineLabs().flatMap { lab ->
            val spec = labs.engineSpecOf(lab.slug)
            val combos = grid(spec)
            listOfNotNull(if (spec.goals.size < 2) "${lab.slug}: ${spec.goals.size} goals, want 2–3" else null) +
                spec.goals.flatMap { goal ->
                    listOfNotNull(
                        if (labs.goalMet(lab.slug, goal, emptyMap())) "${lab.slug}/${goal.id}: already met at the defaults" else null,
                        if (combos.none { labs.goalMet(lab.slug, goal, it) }) "${lab.slug}/${goal.id}: unreachable with the lab's knobs" else null,
                    )
                }
        }
        assertThat(problems).isEmpty()
    }

    @Test
    fun `every lab sweeps each numeric knob and replays a change that the engine notices`() {
        val problems = engineLabs().flatMap { lab ->
            val spec = labs.engineSpecOf(lab.slug)
            val sweeps = spec.knobs.filter { it.type == "number" }.mapNotNull { knob ->
                val points = labs.sweep(lab.slug, EngineSweepRequest(knob = knob.trait)).points
                if (points.size >= 2 && points.map { it.value }.distinct().size == points.size) null else "${lab.slug}: sweep of ${knob.trait} gave ${points.size} points"
            }
            // Flip every boolean knob on (the labs' fixes are switches), applied at 120 s: the replay's last point must differ from doing nothing.
            val change: Map<String, Any?> = spec.knobs.filter { it.type == "boolean" }.associate { it.trait to true }
            val replay = labs.replay(lab.slug, EngineReplayRequest(traits = change, applyAt = listOf(120))).resolved!!
            val moved = spec.watch.any { m -> replay.series[0].values.getValue(m).last() != replay.series[1].values.getValue(m).last() }
            sweeps + listOfNotNull(if (moved) null else "${lab.slug}: replaying $change changed nothing")
        }
        assertThat(problems).isEmpty()
    }

    @Test
    fun `every concept the content explains with engine numbers has a lab on that domain`() {
        val labsByConcept = engineLabs().flatMap { lab ->
            (listOfNotNull(lab.riskKey) + labs.engineSpecOf(lab.slug).concepts).map { it to lab.domain }
        }.groupBy({ it.first }, { it.second })
        val problems = conceptRepository.findAll().mapNotNull { concept ->
            val domains = contentBlocks.parse(concept.blocks).mapNotNull {
                when (it) {
                    is NumbersBlock -> it.domain
                    is TimelineBlock -> it.domain
                    else -> null
                }
            }.toSet()
            if (domains.isEmpty() || labsByConcept[concept.riskKey].orEmpty().any { it in domains }) null
            else "${concept.riskKey} is explained with ${domains} numbers but no lab on those domains lists it"
        }
        assertThat(problems).isEmpty()
        val unknown = engineLabs().flatMap { labs.engineSpecOf(it.slug).concepts } - conceptRepository.findAll().map { it.riskKey }.toSet()
        assertThat(unknown).isEmpty()
    }

    @Test
    fun `the concept page lists labs that exercise it, its own lab first`() {
        val body = mockMvc.perform(get("/learning/concepts/MISSING_READ_REPLICA").header("Authorization", bearerHeader(user)))
            .andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(body, "$.labs")).containsExactly("engine-cache-stampede")
    }

    @Test
    fun `the new capacity problems work out to the guides' scale`() {
        fun truths(slug: String, variant: Int = 0): Map<String, Double> {
            val spec = labs.capacitySpec(labRepository.findBySlug(slug)!!)
            return labs.truths(spec, spec.variants[variant])
        }
        // 1M × 0.8 × 5 requests over 10 s; 10,000 coupons × 2 queries over 10 s; 2,000 q/s × 5 ms = 10 connections (Little's law).
        assertThat(truths("capacity-coupon-open")).containsEntry("peakRps", 400_000.0).containsEntry("writeQps", 2_000.0).containsEntry("connections", 10.0)
        assertThat(truths("capacity-coupon-open", 1).getValue("connections")).isEqualTo(50.0)
        // 20M over 30 min ≈ 11,111/s; × 0.2 s ≈ 2,222 in flight; ÷ 10 per consumer ≈ 222 consumers.
        assertThat(truths("capacity-notification-blast").getValue("consumers")).isCloseTo(222.2, Offset.offset(0.1))
        // 2M × 0.3 / 86,400 ≈ 6.9 TPS; × 10 ≈ 69 at peak; × 1 s ≈ 69 payments held at once (× 3 when PG takes 3 s).
        assertThat(truths("capacity-payment-peak").getValue("inFlight")).isCloseTo(69.4, Offset.offset(0.1))
        assertThat(truths("capacity-payment-peak", 1).getValue("inFlight")).isCloseTo(208.3, Offset.offset(0.1))
    }

    @Test
    fun `a sweep walks one knob across its range with the engine`() {
        val body = mockMvc.perform(
            post("/learning/labs/engine-cache-stampede/engine/sweep").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user))
                .content("""{"traits": {"singleFlightEnabled": true, "cachePolicySplit": true}, "knob": "readReplicaCount"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<Int>>(body, "$.points[*].value")).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        // Single-flight + split: 2,000 rps of misses over 2,000 × (1 + replicas) — 1 replica halves it to 50%, 3 to 25%.
        val load = JsonPath.read<List<Double>>(body, "$.points[*].state.dbReadLoad")
        assertThat(load[1]).isCloseTo(0.5, Offset.offset(0.001))
        assertThat(load[3]).isCloseTo(0.25, Offset.offset(0.001))

        mockMvc.perform(
            post("/learning/labs/engine-cache-stampede/engine/sweep").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user)).content("""{"knob": "singleFlightEnabled"}"""),
        ).andExpect(status().isBadRequest)

        // Chunk size spans three decades: points are spread on a log axis, so the small-chunk end isn't squashed.
        val chunks = mockMvc.perform(
            post("/learning/labs/engine-batch-chunk/engine/sweep").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user)).content("""{"knob": "chunkSize"}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<Boolean>(chunks, "$.logScale")).isTrue()
        val values = JsonPath.read<List<Int>>(chunks, "$.points[*].value")
        assertThat(values.first()).isEqualTo(100)
        assertThat(values.last()).isEqualTo(100_000)
        // A linear grid would put one point below 1,000; the log grid puts most of 100…1,000 (step 100) there.
        assertThat(values.count { it <= 1000 }).isGreaterThanOrEqualTo(8)
    }

    @Test
    fun `a replay applies the lab's settings at the chosen second and late costs a bigger backlog`() {
        val body = mockMvc.perform(
            post("/learning/labs/engine-retry-storm/engine/replay").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user))
                .content("""{"traits": {"consumerCount": 12, "retryBackoffMultiplier": 10, "circuitBreakerEnabled": true}, "applyAt": [180, 60]}"""),
        ).andExpect(status().isOk).andReturn().response.contentAsString
        assertThat(JsonPath.read<List<String>>(body, "$.resolved.series[*].label")).containsExactly("아무것도 하지 않으면", "60초에 적용", "180초에 적용")
        assertThat(JsonPath.read<List<Int>>(body, "$.resolved.series[*].switchAt")).containsExactly(null, 60, 180)
        val seconds = JsonPath.read<List<Int>>(body, "$.resolved.seconds")
        val early = JsonPath.read<List<Number>>(body, "$.resolved.series[1].values.queueLag").map { it.toDouble() }
        val late = JsonPath.read<List<Number>>(body, "$.resolved.series[2].values.queueLag").map { it.toDouble() }
        val at = seconds.indexOf(180)
        // Notification's backlog accumulates until the fix lands, then drains — applying at 60 s leaves far less piled up at 180 s.
        assertThat(early[at]).isLessThan(late[at])
        assertThat(early.last()).isLessThanOrEqualTo(late.last())

        mockMvc.perform(
            post("/learning/labs/engine-retry-storm/engine/replay").contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", bearerHeader(user)).content("""{"traits": {"dbPoolSize": 100}}"""),
        ).andExpect(status().isBadRequest)
    }
}
