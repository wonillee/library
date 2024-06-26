package io.chrislee.library.lending.patron.application.usecase

import arrow.core.Either
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancellingFailedEvent
import io.chrislee.library.lending.patron.application.domain.PatronId
import java.time.Instant

internal interface CancelHoldUseCase {
    suspend fun execute(command: CancelHoldCommand): Either<BookHoldCancellingFailedEvent, BookHoldCancelledEvent>
}

internal data class CancelHoldCommand(
    val patronId: PatronId,
    val bookId: BookId,
    val occurredAt: Instant = Instant.now(),
)
