package com.sysdrill.backend.common.web

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * CORS for the regular API (management.endpoints.web.cors in
 * application.yml covers only the actuator endpoints, not these).
 */
@Configuration
class CorsConfig(
    @Value("\${sysdrill.frontend-origin:http://localhost:3000}") private val frontendOrigin: String,
) : WebMvcConfigurer {

    override fun addCorsMappings(registry: CorsRegistry) {
        registry.addMapping("/**")
            .allowedOrigins(frontendOrigin)
            .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            .allowedHeaders("*")
    }
}
