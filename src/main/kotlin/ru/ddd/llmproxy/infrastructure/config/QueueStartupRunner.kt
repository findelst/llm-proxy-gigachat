package ru.ddd.llmproxy.infrastructure.config

import dev.langchain4j.model.chat.request.ChatRequest
import dev.langchain4j.model.chat.response.ChatResponse
import mu.KotlinLogging
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.stereotype.Component
import ru.ddd.llmproxy.application.port.ChatProviderPort
import ru.ddd.llmproxy.infrastructure.queue.CoroutinePriorityQueue

private val log = KotlinLogging.logger {}

/**
 * ApplicationRunner that starts queue processing on application startup.
 *
 * This ensures that the CoroutinePriorityQueue begins consuming requests
 * from all priority channels as soon as the Spring context is fully initialized.
 */
@Component
class QueueStartupRunner(
    private val priorityQueue: CoroutinePriorityQueue<ChatRequest, ChatResponse>,
    private val chatProvider: ChatProviderPort<ChatRequest>
) : ApplicationRunner {

    override fun run(args: ApplicationArguments?) {
        log.info { "Starting queue processing on application startup" }

        priorityQueue.startProcessing { request ->
            chatProvider.generate(request)
        }

        log.info { "Queue processing started successfully" }
    }
}
