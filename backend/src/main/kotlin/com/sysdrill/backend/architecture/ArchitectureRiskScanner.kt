package com.sysdrill.backend.architecture

import com.sysdrill.backend.evaluation.RuleFinding
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.media.ArraySchema
import io.swagger.v3.oas.models.parameters.Parameter

/**
 * The deterministic half of Phase 6's Architecture Linter (docs/adr/0034) —
 * same shape as [com.sysdrill.backend.evaluation.RuleEvaluator]: fixed rules
 * scan a structured input and emit [RuleFinding]s, no LLM involved. v1 is
 * scoped to what a single OpenAPI document can prove on its own — no
 * multi-service risks (SPOF, contract drift) since those need a real System
 * Graph across services, which is out of scope until repository import
 * exists.
 */
object ArchitectureRiskScanner {

    private val MUTATING_METHODS = setOf(PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH, PathItem.HttpMethod.DELETE)
    private val PAGINATION_PARAM_NAMES = setOf("page", "limit", "size", "cursor", "offset", "pagesize", "pagetoken")

    fun scan(openApi: OpenAPI): List<RuleFinding> {
        val findings = mutableListOf<RuleFinding>()
        val paths = openApi.paths ?: return findings

        paths.forEach { (path, pathItem) ->
            pathItem.readOperationsMap().forEach { (method, operation) ->
                val label = "$method $path"
                val responseCodes = operation.responses?.keys.orEmpty()

                if (responseCodes.none { it.startsWith("4") || it.startsWith("5") }) {
                    findings.add(
                        RuleFinding(
                            "NO_ERROR_RESPONSES",
                            "MEDIUM",
                            "$label 에 4xx/5xx 에러 응답 정의가 없습니다. 실패 상황에서 클라이언트가 무엇을 받을지 계약이 없습니다.",
                        )
                    )
                }

                if (method in MUTATING_METHODS && operation.security.isNullOrEmpty() && openApi.security.isNullOrEmpty()) {
                    findings.add(
                        RuleFinding(
                            "NO_SECURITY_SCHEME",
                            "HIGH",
                            "$label 는 상태를 변경하는 요청인데 인증 요구사항(security)이 없습니다.",
                        )
                    )
                }

                if (method == PathItem.HttpMethod.GET && returnsUnboundedList(operation) && !hasPaginationParam(operation)) {
                    findings.add(
                        RuleFinding(
                            "UNBOUNDED_LIST_RESPONSE",
                            "MEDIUM",
                            "$label 는 배열을 응답하는데 page/limit/cursor류 페이지네이션 파라미터가 없습니다. 데이터가 늘면 응답이 무한정 커질 수 있습니다.",
                        )
                    )
                }

                if (method in setOf(PathItem.HttpMethod.POST, PathItem.HttpMethod.PUT, PathItem.HttpMethod.PATCH) &&
                    operation.requestBody?.content.isNullOrEmpty()
                ) {
                    findings.add(
                        RuleFinding(
                            "MISSING_REQUEST_VALIDATION",
                            "MEDIUM",
                            "$label 에 요청 본문 스키마가 정의돼 있지 않습니다. 잘못된 입력을 무엇이 걸러내는지 계약상 알 수 없습니다.",
                        )
                    )
                }
            }
        }
        return findings
    }

    private fun returnsUnboundedList(operation: io.swagger.v3.oas.models.Operation): Boolean {
        val response = operation.responses?.get("200") ?: return false
        val schema = response.content?.values?.firstOrNull()?.schema ?: return false
        return schema is ArraySchema || schema.type == "array"
    }

    private fun hasPaginationParam(operation: io.swagger.v3.oas.models.Operation): Boolean {
        val params: List<Parameter> = operation.parameters.orEmpty()
        return params.any { it.name?.lowercase() in PAGINATION_PARAM_NAMES }
    }
}
