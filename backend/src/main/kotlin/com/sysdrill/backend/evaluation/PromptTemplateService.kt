package com.sysdrill.backend.evaluation

import com.sysdrill.backend.common.web.NotFoundException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class PromptTemplateService(private val repository: PromptTemplateRepository) {

    fun listVersions(purpose: String): List<PromptTemplate> = repository.findByPurposeOrderByVersionDesc(purpose)

    @Transactional
    fun create(purpose: String, templateBody: String): PromptTemplate {
        val nextVersion = (repository.findTopByPurposeOrderByVersionDesc(purpose)?.version ?: 0) + 1
        return repository.save(PromptTemplate(purpose = purpose, version = nextVersion, templateBody = templateBody))
    }

    /** Activates one version and deactivates every other version of the same purpose. */
    @Transactional
    fun activate(id: UUID): PromptTemplate {
        val template = repository.findById(id).orElseThrow { NotFoundException("Prompt template not found: $id") }
        repository.findByPurposeAndActiveTrueAndIdNot(template.purpose, id).forEach {
            it.active = false
            repository.save(it)
        }
        template.active = true
        return repository.save(template)
    }
}
