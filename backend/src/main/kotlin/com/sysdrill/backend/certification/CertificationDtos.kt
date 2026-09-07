package com.sysdrill.backend.certification

import java.util.UUID

data class DomainCertificationStatus(
    val domain: String,
    val title: String,
    val passed: Boolean,
    val bestScore: Int?,
)

data class CertificationStatusResponse(
    val userId: UUID,
    val nickname: String,
    val certified: Boolean,
    val domains: List<DomainCertificationStatus>,
)
