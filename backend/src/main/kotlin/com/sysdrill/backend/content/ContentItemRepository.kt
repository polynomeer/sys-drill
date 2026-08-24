package com.sysdrill.backend.content

import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

interface ContentItemRepository : JpaRepository<ContentItem, UUID>
