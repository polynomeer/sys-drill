package com.sysdrill.backend.certification

import java.util.UUID

data class DomainCertificationStatus(
    val domain: String,
    val title: String,
    val passed: Boolean,
    val bestScore: Int?,
    /**
     * PLAN.md Round E21 (docs/DRILLS_EXPANSION_PLAN.md M10) — different tail-design variants passed
     * (session at or above the passing score). Shown next to [passed], never folded into it or DrillScore.
     */
    val passedVariants: Int = 0,
    /** Variants the current official version offers (1 for a single FOLLOWUP prompt). */
    val totalVariants: Int = 1,
)

data class CertificationStatusResponse(
    val userId: UUID,
    val nickname: String,
    val certified: Boolean,
    val domains: List<DomainCertificationStatus>,
)
