package com.sysdrill.backend.architecture

import com.sysdrill.backend.evaluation.RuleFinding
import io.swagger.v3.oas.models.OpenAPI

/**
 * Renews the frontend's total lack of architecture visualization (no prior
 * ADR/PLAN reference — a direct product gap the user pointed out, inspired by
 * archify's risk-annotated diagrams). Walks the same `OpenAPI.paths` structure
 * [ArchitectureRiskScanner] already scans and emits a Mermaid `flowchart TD`
 * string: one node per endpoint, colored by the highest-severity finding that
 * applies to it. Fully deterministic — no LLM, nothing persisted beyond the
 * derived scenario text, matching docs/adr/0034.
 */
object ArchitectureDiagramGenerator {

    fun generate(openApi: OpenAPI, findings: List<RuleFinding>): String {
        val paths = openApi.paths
        if (paths.isNullOrEmpty()) return "flowchart TD\n    Client([Client])"

        val usedIds = mutableSetOf<String>()
        val edgeLines = mutableListOf<String>()
        val styleLines = mutableListOf<String>()

        paths.forEach { (path, pathItem) ->
            pathItem.readOperationsMap().forEach { (method, _) ->
                val label = "$method $path"
                val id = slug(label, usedIds)
                edgeLines += "    Client --> $id[\"${escape(label)}\"]"

                val severity = findings
                    .filter { it.description.startsWith("$label ") }
                    .maxByOrNull { severityRank(it.severity) }
                    ?.severity
                when (severity) {
                    "HIGH" -> styleLines += "    style $id fill:#fca5a5,stroke:#dc2626,color:#7f1d1d"
                    "MEDIUM" -> styleLines += "    style $id fill:#fde68a,stroke:#d97706,color:#78350f"
                }
            }
        }

        return (listOf("flowchart TD", "    Client([Client])") + edgeLines + styleLines).joinToString("\n")
    }

    private fun severityRank(severity: String): Int = when (severity) {
        "HIGH" -> 2
        "MEDIUM" -> 1
        else -> 0
    }

    private fun escape(label: String): String = label.replace("\"", "'")

    /** Mermaid node ids must be word-safe; collisions (rare, but possible after stripping) get a numeric suffix. */
    private fun slug(label: String, used: MutableSet<String>): String {
        val base = "n" + label.filter { it.isLetterOrDigit() }
        var candidate = base
        var suffix = 1
        while (!used.add(candidate)) {
            candidate = "$base$suffix"
            suffix++
        }
        return candidate
    }
}
