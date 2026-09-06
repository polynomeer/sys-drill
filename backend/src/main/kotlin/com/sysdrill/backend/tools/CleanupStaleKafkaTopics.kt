package com.sysdrill.backend.tools

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import java.util.concurrent.TimeUnit

/**
 * One-off maintenance tool (not a Spring bean, not wired into the app) that
 * deletes leftover realinfra-notify-<uuid> Kafka topics — e.g. from crashed
 * or interrupted [NotificationTopicProvisionerTest] runs against the shared
 * docker-compose broker — that never reached their own `drop()` call. Run
 * via `./scripts/cleanup-stale-kafka-topics.sh`, not directly.
 */
private val STALE_TOPIC_PATTERN = Regex("^realinfra-notify-[0-9a-f]{32}$")

fun main(args: Array<String>) {
    val bootstrapServers = args.firstOrNull { it != "--yes" } ?: "localhost:19092"
    val skipConfirm = args.contains("--yes")

    AdminClient.create(mapOf(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers)).use { admin ->
        val staleTopics = admin.listTopics().names().get(10, TimeUnit.SECONDS)
            .filter { STALE_TOPIC_PATTERN.matches(it) }
            .sorted()

        if (staleTopics.isEmpty()) {
            println("[cleanup] $bootstrapServers 에 정리할 stale realinfra-notify-* 토픽이 없습니다.")
            return
        }

        println("[cleanup] ${staleTopics.size}개의 stale 토픽을 발견했습니다 ($bootstrapServers):")
        staleTopics.forEach { println("  - $it") }

        if (!skipConfirm) {
            print("\n삭제하시겠습니까? 지금 진행 중인 세션이 쓰고 있는 토픽은 없는지 확인하세요. [y/N] ")
            System.out.flush()
            val answer = readLine()?.trim()?.lowercase()
            if (answer != "y" && answer != "yes") {
                println("[cleanup] 취소했습니다.")
                return
            }
        }

        admin.deleteTopics(staleTopics).all().get(30, TimeUnit.SECONDS)
        println("[cleanup] ${staleTopics.size}개 토픽을 삭제했습니다.")
    }
}
