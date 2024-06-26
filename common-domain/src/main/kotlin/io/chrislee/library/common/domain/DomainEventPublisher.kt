package io.chrislee.library.common.domain

import arrow.core.Either

interface DomainEventPublisher {
    fun publish(event: DomainEvent): Either<SystemError, Unit>
}
