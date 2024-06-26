package io.chrislee.library.common.infrastructure

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

class TestContainerKafkaInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val image = DockerImageName.parse("apache/kafka:3.7.0")
        val container = KafkaContainer(image)
        container.start()
        applicationContext.addApplicationListener { e ->
            if (e is ContextClosedEvent) {
                container.stop()
            }
        }
        TestPropertyValues.of(
            mapOf(
                "spring.kafka.bootstrap-servers" to container.bootstrapServers,
            ),
        ).applyTo(applicationContext)
    }
}
