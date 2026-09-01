package com.sysdrill.backend.identity

import java.util.UUID

data class UserResponse(
    val id: UUID,
    val nickname: String,
    val experienceYears: Int?,
    val primaryStack: String?,
) {
    companion object {
        fun from(user: User) = UserResponse(user.id!!, user.nickname, user.experienceYears, user.primaryStack)
    }
}
