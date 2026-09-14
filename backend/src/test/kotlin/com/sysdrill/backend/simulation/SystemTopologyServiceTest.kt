package com.sysdrill.backend.simulation

import com.sysdrill.backend.identity.User
import com.sysdrill.backend.identity.UserRepository
import com.sysdrill.backend.support.startSession
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.web.servlet.MockMvc
import java.util.UUID

/**
 * Fast, focused unit-of-behavior coverage for `wasFieldExplicitlySet` —
 * `RealInfraCouponTimelineTest`'s two topology tests already prove it end to
 * end through a real incident start (HikariCP pool, k6 probe included), but
 * that's expensive and doesn't localize which specific edge case broke. This
 * writes `SystemTopology` rows directly via the repository to check each
 * branch in isolation, cheaply — `system_topologies.session_id` has a real FK
 * to `sessions`, so each test still needs an actual session (via the usual
 * `mockMvc.startSession` helper), just none of the scenario/wargame machinery
 * around it.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SystemTopologyServiceTest(
    @Autowired val systemTopologyService: SystemTopologyService,
    @Autowired val topologyRepository: SystemTopologyRepository,
    @Autowired val mockMvc: MockMvc,
    @Autowired val userRepository: UserRepository,
) {
    private lateinit var userId: UUID

    @BeforeEach
    fun createTestUser() {
        userId = userRepository.save(
            User(email = "topology-svc-${UUID.randomUUID()}@example.com", passwordHash = "hash", nickname = "drill-user")
        ).id!!
    }

    private fun sessionWithGraph(graph: String): UUID {
        val sessionId = mockMvc.startSession(userId)
        topologyRepository.save(SystemTopology(sessionId = sessionId, graph = graph))
        return sessionId
    }

    @Test
    fun `no saved topology at all is not an explicit set`() {
        val sessionId = mockMvc.startSession(userId)
        assertThat(systemTopologyService.wasFieldExplicitlySet(sessionId, "coupon", "dbPoolSize")).isFalse()
    }

    @Test
    fun `a connected node of the wrong kind is not an explicit set`() {
        // "cache" kind, not "db" — coupon's dbPoolSize field only maps to "db" nodes.
        val sessionId = sessionWithGraph(
            """{"nodes":[{"id":"n1","data":{"kind":"cache","traitValues":{"dbPoolSize":50}}},{"id":"n2","data":{"kind":"cache","traitValues":{}}}],"edges":[{"source":"n1","target":"n2"}]}"""
        )
        assertThat(systemTopologyService.wasFieldExplicitlySet(sessionId, "coupon", "dbPoolSize")).isFalse()
    }

    @Test
    fun `a connected db node without the dbPoolSize key is not an explicit set`() {
        // Right kind, but the node never set this particular field.
        val sessionId = sessionWithGraph(
            """{"nodes":[{"id":"n1","data":{"kind":"db","traitValues":{}}},{"id":"n2","data":{"kind":"db","traitValues":{}}}],"edges":[{"source":"n1","target":"n2"}]}"""
        )
        assertThat(systemTopologyService.wasFieldExplicitlySet(sessionId, "coupon", "dbPoolSize")).isFalse()
    }

    @Test
    fun `an orphaned db node with dbPoolSize set is not an explicit set`() {
        // Right kind and key, but no edge attaches it to anything — decorative/orphaned.
        val sessionId = sessionWithGraph("""{"nodes":[{"id":"n1","data":{"kind":"db","traitValues":{"dbPoolSize":50}}}],"edges":[]}""")
        assertThat(systemTopologyService.wasFieldExplicitlySet(sessionId, "coupon", "dbPoolSize")).isFalse()
    }

    @Test
    fun `a connected db node with dbPoolSize set is an explicit set, even at the rule-based default value`() {
        // 50 == DesignTraits.DEFAULT_DB_POOL_SIZE — the exact value-collision this method exists to disambiguate.
        val sessionId = sessionWithGraph(
            """{"nodes":[{"id":"n1","data":{"kind":"db","traitValues":{"dbPoolSize":50}}},{"id":"n2","data":{"kind":"db","traitValues":{}}}],"edges":[{"source":"n1","target":"n2"}]}"""
        )
        assertThat(systemTopologyService.wasFieldExplicitlySet(sessionId, "coupon", "dbPoolSize")).isTrue()
    }

    @Test
    fun `an unknown domain is not an explicit set`() {
        val sessionId = sessionWithGraph(
            """{"nodes":[{"id":"n1","data":{"kind":"db","traitValues":{"dbPoolSize":50}}},{"id":"n2","data":{"kind":"db","traitValues":{}}}],"edges":[{"source":"n1","target":"n2"}]}"""
        )
        assertThat(systemTopologyService.wasFieldExplicitlySet(sessionId, "not-a-real-domain", "dbPoolSize")).isFalse()
    }
}
