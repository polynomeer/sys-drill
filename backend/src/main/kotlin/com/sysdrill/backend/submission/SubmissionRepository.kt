package com.sysdrill.backend.submission

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface SubmissionRepository : JpaRepository<Submission, UUID>
