package com.sysdrill.backend.evaluation

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface EvaluationRepository : JpaRepository<Evaluation, UUID>

interface EvaluationRiskFlagRepository : JpaRepository<EvaluationRiskFlag, UUID>
