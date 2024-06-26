package io.chrislee.library.common.infrastructure

import io.chrislee.library.common.domain.DomainEventPublisher
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class DomainEventPublisherConfiguration {
    @Bean
    fun domainEventPublisher(applicationEventPublisher: ApplicationEventPublisher): DomainEventPublisher {
        return SpringDomainEventPublisher(applicationEventPublisher)
    }
}
