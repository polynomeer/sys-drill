package com.sysdrill.backend.simulation

/** The structured schema the system prompt (see V42 migration) instructs the model to return. */
data class DirectorNarrationResult(
    val narration: String? = null,
)
