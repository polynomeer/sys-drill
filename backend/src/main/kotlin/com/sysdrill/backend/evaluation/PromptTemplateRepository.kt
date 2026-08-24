package com.sysdrill.backend.evaluation

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface PromptTemplateRepository : JpaRepository<PromptTemplate, UUID> {
    fun findFirstByPurposeAndActiveTrue(purpose: String): PromptTemplate?
    fun findTopByPurposeOrderByVersionDesc(purpose: String): PromptTemplate?
    fun findByPurposeOrderByVersionDesc(purpose: String): List<PromptTemplate>
    fun findByPurposeAndActiveTrueAndIdNot(purpose: String, id: UUID): List<PromptTemplate>
}
