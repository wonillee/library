package io.chrislee.library.lending.patron.adapter.eventhandler

import io.chrislee.library.common.domain.DomainEventPublisher
import io.chrislee.library.common.infrastructure.transactionalEither
import io.chrislee.library.lending.book.application.domain.BookDuplicateHoldFoundEvent
import io.chrislee.library.lending.patron.application.usecase.HandleDuplicateHoldUseCase
import org.springframework.context.event.EventListener
import org.springframework.transaction.reactive.TransactionalOperator

internal class DuplicateHoldHandler(
    private val transactionalOperator: TransactionalOperator,
    private val domainEventPublisher: DomainEventPublisher,
    private val useCase: HandleDuplicateHoldUseCase,
) {
    @EventListener
    suspend fun listen(event: BookDuplicateHoldFoundEvent) {
        transactionalEither(transactionalOperator) {
            useCase.handle(event).fold(
                ifLeft = { domainEventPublisher.publish(it).bind() },
                ifRight = { domainEventPublisher.publish(it).bind() },
            )
        }
            .onLeft { logger.error("이벤트: $event, 오류: ${it.message}") }
    }

    companion object {
        private val logger = mu.KotlinLogging.logger { }
    }
}
