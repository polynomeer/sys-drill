package com.sysdrill.backend.identity

/** Build language the learner wants /bridge to open in. Matches the two rate-limiter challenge variants. */
enum class PreferredLanguage { PYTHON, TYPESCRIPT }

/** Why the learner is here — each value maps to one concrete UI default (see V51). */
enum class TrainingGoal { INTERVIEW, SKILLS, TEAM }

data class UserPreferencesRequest(
    val preferredLanguage: PreferredLanguage? = null,
    val trainingGoal: TrainingGoal? = null,
)

data class UserPreferencesResponse(
    val preferredLanguage: PreferredLanguage?,
    val trainingGoal: TrainingGoal?,
) {
    companion object {
        fun from(user: User) = UserPreferencesResponse(user.preferredLanguage, user.trainingGoal)
    }
}
