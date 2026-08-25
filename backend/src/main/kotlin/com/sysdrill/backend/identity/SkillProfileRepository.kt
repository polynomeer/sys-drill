package com.sysdrill.backend.identity

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SkillProfileRepository : JpaRepository<SkillProfile, UUID> {
    fun findByUserId(userId: UUID): SkillProfile?
}
