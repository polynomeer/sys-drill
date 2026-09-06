package com.sysdrill.backend.organization

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface OrganizationCurriculumStepRepository : JpaRepository<OrganizationCurriculumStep, UUID> {
    fun findByOrganizationIdOrderByStepOrder(organizationId: UUID): List<OrganizationCurriculumStep>
    fun deleteByOrganizationId(organizationId: UUID)
}
