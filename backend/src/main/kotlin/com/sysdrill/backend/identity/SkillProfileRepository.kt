package com.sysdrill.backend.identity

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SkillProfileRepository : JpaRepository<SkillProfile, UUID> {
    fun findByUserId(userId: UUID): SkillProfile?

    /** PLAN.md step 33 — the org dashboard's per-member roster; one query for every member instead of N. */
    fun findByUserIdIn(userIds: Collection<UUID>): List<SkillProfile>
}
