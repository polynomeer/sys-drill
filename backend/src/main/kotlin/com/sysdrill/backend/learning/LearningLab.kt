package com.sysdrill.backend.learning

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import org.springframework.data.jpa.repository.JpaRepository
import java.util.UUID

/** docs/LEARNING_EXPANSION_PLAN.md L5·L6 — one lab. `spec`'s shape depends on [kind] (see V58). */
@Entity
@Table(name = "learning_labs")
class LearningLab(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null,

    @Column(nullable = false, unique = true)
    var slug: String,

    /** CAPACITY (L6) or ENGINE (L5). */
    @Column(nullable = false)
    var kind: String,

    @Column(name = "risk_key")
    var riskKey: String? = null,

    var domain: String? = null,

    @Column(nullable = false)
    var title: String,

    @Column(nullable = false)
    var summary: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var spec: String,

    @Column(name = "display_order", nullable = false)
    var displayOrder: Int = 0,
)

interface LearningLabRepository : JpaRepository<LearningLab, UUID> {
    fun findAllByOrderByDisplayOrderAsc(): List<LearningLab>
    fun findBySlug(slug: String): LearningLab?
}

/**
 * The Capacity Lab's answers aren't stored: each ask carries a formula over the
 * problem's inputs (and earlier asks), computed here. A tiny recursive-descent
 * parser for + − × ÷, parentheses, numbers and identifiers — nothing else is
 * evaluated, so seeded content can't run arbitrary code.
 */
object CapacityFormula {

    fun evaluate(formula: String, variables: Map<String, Double>): Double = Parser(formula, variables).parse()

    private class Parser(private val src: String, private val vars: Map<String, Double>) {
        private var pos = 0

        fun parse(): Double {
            val value = expression()
            skipSpaces()
            require(pos == src.length) { "Unexpected '${src.substring(pos)}' in formula: $src" }
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '+' -> { pos++; value + term() }
                    '-' -> { pos++; value - term() }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = factor()
            while (true) {
                skipSpaces()
                value = when (peek()) {
                    '*' -> { pos++; value * factor() }
                    '/' -> { pos++; value / factor() }
                    else -> return value
                }
            }
        }

        private fun factor(): Double {
            skipSpaces()
            val c = peek() ?: error("Unexpected end of formula: $src")
            return when {
                c == '(' -> { pos++; val v = expression(); skipSpaces(); require(peek() == ')') { "Missing ')' in $src" }; pos++; v }
                c == '-' -> { pos++; -factor() }
                c.isDigit() || c == '.' -> number()
                c.isLetter() -> identifier()
                else -> error("Unexpected '$c' in formula: $src")
            }
        }

        private fun number(): Double {
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            return src.substring(start, pos).toDouble()
        }

        private fun identifier(): Double {
            val start = pos
            while (peek()?.let { it.isLetterOrDigit() || it == '_' } == true) pos++
            val name = src.substring(start, pos)
            return vars[name] ?: error("Unknown variable '$name' in formula: $src")
        }

        private fun peek(): Char? = src.getOrNull(pos)
        private fun skipSpaces() { while (peek()?.isWhitespace() == true) pos++ }
    }
}
