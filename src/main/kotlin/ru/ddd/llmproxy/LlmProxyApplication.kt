package ru.ddd.llmproxy

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import ru.ddd.llmproxy.infrastructure.config.LlmProxyProperties

@SpringBootApplication
@EnableConfigurationProperties(LlmProxyProperties::class)
class LlmProxyApplication

fun main(args: Array<String>) {
    org.springframework.boot.runApplication<LlmProxyApplication>(*args)
}
