package io.chrislee.library.lending.book.application.usecase

import arrow.core.Either
import io.chrislee.library.common.domain.InvalidStateError
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.Book
import io.chrislee.library.lending.book.application.domain.BookDuplicateHoldFoundEvent
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent

internal sealed class ErrorOnHandlingPatronEvent {
    data class System(val systemError: SystemError) : ErrorOnHandlingPatronEvent()

    data class BookDuplicateHoldFound(val eventToRaise: BookDuplicateHoldFoundEvent) : ErrorOnHandlingPatronEvent()

    data class InvalidState(val invalidStateError: InvalidStateError) : ErrorOnHandlingPatronEvent()
}

internal interface BookPlacedOnHoldEventFiredUseCase {
    suspend fun handle(event: BookPlacedOnHoldEvent): Either<ErrorOnHandlingPatronEvent, Book>
}

internal interface BookCheckedOutEventFiredUseCase {
    suspend fun handle(event: BookCheckedOutEvent): Either<ErrorOnHandlingPatronEvent, Book>
}

internal interface BookHoldExpiredEventFiredUseCase {
    suspend fun handle(event: BookHoldExpiredEvent): Either<ErrorOnHandlingPatronEvent, Book>
}

internal interface BookHoldCancelledEventFiredUseCase {
    suspend fun handle(event: BookHoldCancelledEvent): Either<ErrorOnHandlingPatronEvent, Book>
}

internal interface BookReturnedEventFiredUseCase {
    suspend fun handle(event: BookReturnedEvent): Either<ErrorOnHandlingPatronEvent, Book>
}
