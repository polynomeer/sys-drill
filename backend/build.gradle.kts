plugins {
	kotlin("jvm") version "2.3.21"
	kotlin("plugin.spring") version "2.3.21"
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "2.3.21"
}

group = "com.sysdrill"
version = "0.0.1-SNAPSHOT"
description = "SysDrill backend (modular monolith)"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	// PLAN.md step 24 — real distributed tracing for the real-infra coupon
	// pilot, exported via OTLP to a real Jaeger container (docker-compose).
	// Spring Boot 4's dedicated starter, not the manual Boot-3-era combo of
	// micrometer-tracing-bridge-otel + opentelemetry-exporter-otlp directly —
	// without this starter, Boot 4's tracing autoconfiguration classes
	// (OtlpTracingAutoConfiguration etc.) never even get evaluated, since
	// they moved out of spring-boot-actuator-autoconfigure into a module this
	// starter alone pulls in (confirmed empirically: the manual combo
	// compiled fine but silently produced zero traces).
	implementation("org.springframework.boot:spring-boot-starter-opentelemetry")
	implementation("org.flywaydb:flyway-database-postgresql")
	// PLAN.md step 27 — real-infra notification pilot only. The plain Kafka
	// client library (producer/consumer/admin), not spring-kafka's managed
	// listener containers — this pilot needs to dynamically resize how many
	// consumer threads run per session on every action, which is simpler to
	// drive directly than through Spring's declarative @KafkaListener beans
	// (same "raw client over framework abstraction" choice as CouponLoadRunner
	// shelling out to `docker run` instead of using testcontainers).
	implementation("org.apache.kafka:kafka-clients:3.9.0")
	// PLAN.md step 30 — real authentication. Just the crypto module for
	// BCryptPasswordEncoder, not the full spring-boot-starter-security — that
	// starter auto-configures a filter chain/CSRF/default login page that
	// would collide with this app's existing plain-REST CORS setup
	// (CorsConfig.kt). Version comes from Spring Boot's own dependency BOM
	// (io.spring.dependency-management), same as every other unversioned
	// implementation() line here.
	implementation("org.springframework.security:spring-security-crypto")
	// Token signing is the one place in this codebase that deliberately does
	// NOT follow the "hand-roll something low-tech" pattern used elsewhere
	// (EvaluationQueue's Redis encoding, CouponLoadRunner's raw docker CLI) —
	// a well-audited JWT library, not hand-rolled HMAC signing.
	implementation("com.auth0:java-jwt:4.4.0")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("tools.jackson.module:jackson-module-kotlin")
	runtimeOnly("org.postgresql:postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
	testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Loads backend/.env.local (if present) as environment variables for local
// `bootRun` only — never for `test`, which relies on AnthropicLlmClient's
// offline fallback and shouldn't depend on (or require) a real API key.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
	val dotenvLocal = file(".env.local")
	if (dotenvLocal.exists()) {
		dotenvLocal.readLines()
			.map { it.trim() }
			.filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
			.forEach { line ->
				val (key, value) = line.split("=", limit = 2)
				environment(key.trim(), value.trim().removeSurrounding("\""))
			}
	}
}
