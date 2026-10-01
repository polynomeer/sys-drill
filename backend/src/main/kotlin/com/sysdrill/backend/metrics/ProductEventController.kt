package com.sysdrill.backend.metrics

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

data class ProductEventRequest(val name: String)

/**
 * Public on purpose — the Drill overview is viewable logged out, and nothing
 * about the caller is read or stored. Unknown names are a 400, so the table
 * can't be filled with arbitrary keys.
 */
@RestController
class ProductEventController(private val productEventService: ProductEventService) {

    @PostMapping("/events")
    fun record(@RequestBody request: ProductEventRequest): ResponseEntity<Void> {
        productEventService.record(request.name)
        return ResponseEntity.noContent().build()
    }
}
