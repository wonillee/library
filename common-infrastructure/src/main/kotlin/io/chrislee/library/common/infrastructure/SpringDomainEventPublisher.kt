package io.chrislee.library.common.infrastructure

import arrow.core.Either
import arrow.core.raise.either
import io.chrislee.library.common.domain.DomainEvent
import io.chrislee.library.common.domain.DomainEventPublisher
import io.chrislee.library.common.domain.SystemError
import org.springframework.context.ApplicationEventPublisher

class SpringDomainEventPublisher(
    private val applicationEventPublisher: ApplicationEventPublisher,
) : DomainEventPublisher {
    override fun publish(event: DomainEvent): Either<SystemError, Unit> {
        return either {
            Either.catch { applicationEventPublisher.publishEvent(event) }
                .mapLeft { transformToSystemError(it) }
                .bind()
        }
    }
}
