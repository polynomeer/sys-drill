package com.sysdrill.backend.evaluation

import tools.jackson.databind.ObjectMapper

/** Small helpers for reading the jsonb-backed String columns on [Evaluation] back into real types. */

fun ObjectMapper.readStringList(json: String?): List<String> =
    if (json.isNullOrBlank()) emptyList() else readValue(json, Array<String>::class.java).toList()

@Suppress("UNCHECKED_CAST")
fun ObjectMapper.readIntMap(json: String?): Map<String, Int> =
    if (json.isNullOrBlank()) {
        emptyMap()
    } else {
        (readValue(json, Map::class.java) as Map<String, Any>).mapValues { (_, v) -> (v as Number).toInt() }
    }
