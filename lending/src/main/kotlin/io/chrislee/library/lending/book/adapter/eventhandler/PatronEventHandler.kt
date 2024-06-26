package io.chrislee.library.lending.book.adapter.eventhandler

import arrow.core.Either
import arrow.core.raise.either
import io.chrislee.library.common.domain.DomainEventPublisher
import io.chrislee.library.common.infrastructure.transactionalEither
import io.chrislee.library.lending.book.application.domain.Book
import io.chrislee.library.lending.book.application.usecase.BookCheckedOutEventFiredUseCase
import io.chrislee.library.lending.book.application.usecase.BookHoldCancelledEventFiredUseCase
import io.chrislee.library.lending.book.application.usecase.BookHoldExpiredEventFiredUseCase
import io.chrislee.library.lending.book.application.usecase.BookPlacedOnHoldEventFiredUseCase
import io.chrislee.library.lending.book.application.usecase.BookReturnedEventFiredUseCase
import io.chrislee.library.lending.book.application.usecase.ErrorOnHandlingPatronEvent
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent
import io.chrislee.library.lending.patron.application.domain.PatronEvent
import org.springframework.context.event.EventListener
import org.springframework.transaction.reactive.TransactionalOperator

internal class PatronEventHandler(
    private val transactionalOperator: TransactionalOperator,
    private val domainEventPublisher: DomainEventPublisher,
    private val bookPlacedOnHoldEventFiredUseCase: BookPlacedOnHoldEventFiredUseCase,
    private val bookCheckedOutEventFiredUseCase: BookCheckedOutEventFiredUseCase,
    private val bookHoldExpiredEventFiredUseCase: BookHoldExpiredEventFiredUseCase,
    private val bookHoldCancelledEventFiredUseCase: BookHoldCancelledEventFiredUseCase,
    private val bookReturnedEventFiredUseCase: BookReturnedEventFiredUseCase,
) {
    @EventListener(
        value = [
            BookPlacedOnHoldEvent::class,
            BookCheckedOutEvent::class,
            BookHoldExpiredEvent::class,
            BookHoldCancelledEvent::class,
            BookReturnedEvent::class,
        ],
    )
    suspend fun handle(event: PatronEvent) {
        transactionalEither(transactionalOperator) { delegate(event).bind() }
            .onRight { book -> logger.info { "이벤트 처리 완료: $event -> $book" } }
            .onLeft { logger.error { it } }
    }

    private suspend fun delegate(event: PatronEvent) =
        either {
            when (event) {
                is BookPlacedOnHoldEvent -> bookPlacedOnHoldEventFiredUseCase.handle(event)
                    .onLeft { error ->
                        if (error is ErrorOnHandlingPatronEvent.BookDuplicateHoldFound) {
                            domainEventPublisher.publish(error.eventToRaise).mapLeft { it.message }.bind()
                        }
                    }
                    .mapLeftAndAlarm()
                    .bind()

                is BookCheckedOutEvent -> bookCheckedOutEventFiredUseCase.handle(event).mapLeftAndAlarm().bind()
                is BookHoldExpiredEvent -> bookHoldExpiredEventFiredUseCase.handle(event).mapLeftAndAlarm().bind()
                is BookHoldCancelledEvent -> bookHoldCancelledEventFiredUseCase.handle(event).mapLeftAndAlarm().bind()
                is BookReturnedEvent -> bookReturnedEventFiredUseCase.handle(event).mapLeftAndAlarm().bind()
                else -> raise("구독 불가능한 이벤트 발생: $event")
            }
        }

    private fun Either<ErrorOnHandlingPatronEvent, Book>.mapLeftAndAlarm(): Either<String, Book> {
        onLeft {
            // TODO alarm it when necessary
            logger.error { "경보: $it" }
        }
        return mapLeft {
            when (it) {
                is ErrorOnHandlingPatronEvent.BookDuplicateHoldFound -> "중복 대여 예약 발생: ${it.eventToRaise}"
                is ErrorOnHandlingPatronEvent.InvalidState -> it.invalidStateError.message
                is ErrorOnHandlingPatronEvent.System -> it.systemError.message
            }
        }
    }

    private companion object {
        private val logger = mu.KotlinLogging.logger { }
    }
}
