package com.sysdrill.backend.identity

import com.sysdrill.backend.common.readIntList
import com.sysdrill.backend.common.readIntMap
import java.util.UUID
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.ObjectMapper

data class SkillProfileResponse(
    val userId: UUID,
    val weaknesses: Map<String, Int>,
    val trend: List<Int>,
)

/** docs/PRD.md §11.3's "약점 프로필" — GET /users/{id}/skill-profile (no ARCHITECTURE.md
 * precedent; the dashboard's "약점 TOP 3"/"점수 추이" panels need this). */
@RestController
class SkillProfileController(
    private val repository: SkillProfileRepository,
    private val objectMapper: ObjectMapper,
) {

    @GetMapping("/users/{userId}/skill-profile")
    fun get(@PathVariable userId: UUID): SkillProfileResponse {
        val profile = repository.findByUserId(userId)
        return SkillProfileResponse(
            userId = userId,
            weaknesses = objectMapper.readIntMap(profile?.weaknesses),
            trend = objectMapper.readIntList(profile?.trend),
        )
    }
}
