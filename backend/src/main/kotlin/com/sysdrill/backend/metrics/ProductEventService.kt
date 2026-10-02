package com.sysdrill.backend.metrics

import com.sysdrill.backend.common.web.BadRequestException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * docs/CODECRAFTERS_BENCHMARK.md §6 (PLAN.md Round B17) — anonymous daily
 * counters for the few success metrics a database row can't answer. One
 * `(day, name)` row incremented in place; nothing identifies who triggered it.
 */
@Service
class ProductEventService(private val jdbc: JdbcTemplate) {

    fun record(name: String, today: LocalDate = LocalDate.now(ZoneOffset.UTC)) {
        if (name !in ALLOWED) throw BadRequestException("Unknown event: $name")
        jdbc.update(
            "insert into product_event_counts(day, name, count) values (?, ?, 1) " +
                "on conflict (day, name) do update set count = product_event_counts.count + 1",
            today,
            name,
        )
    }

    /** Sum per event over the last [days] days (UTC), zero-filled for every allowed name. */
    fun totals(days: Long, today: LocalDate = LocalDate.now(ZoneOffset.UTC)): Map<String, Long> {
        val rows = jdbc.queryForList(
            "select name, sum(count) as total from product_event_counts where day > ? group by name",
            today.minusDays(days),
        )
        val found = rows.associate { it["name"] as String to (it["total"] as Number).toLong() }
        return ALLOWED.associateWith { found[it] ?: 0L }
    }

    companion object {
        const val OVERVIEW_VIEW = "drill_overview_view"
        const val OVERVIEW_START = "drill_overview_start"
        val ALLOWED = setOf(
            OVERVIEW_VIEW,
            OVERVIEW_START,
            "certifications_view",
            "organizations_view",
            "architecture_analysis_view",
            // docs/LEARNING_EXPANSION_PLAN.md §8 — 피드백에서 개념으로 넘어가는 비율
            "report_view",
            "feedback_concept_click",
            // docs/OBSERVABILITY_UI_PLAN.md §9 — are the investigation tabs actually used during an incident
            "observe_tab_map",
            "observe_tab_alerts",
            "observe_tab_metrics",
            "observe_tab_logs",
            "observe_tab_changes",
            "observe_tab_traces",
        )
    }
}
