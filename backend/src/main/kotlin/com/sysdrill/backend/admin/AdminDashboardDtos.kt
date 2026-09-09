package com.sysdrill.backend.admin

data class AdminDashboardStatsResponse(
    val totalUsers: Long,
    val newUsersToday: Long,
    val totalOrganizations: Long,
    val sessionsCompletedToday: Long,
)
