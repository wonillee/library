package io.chrislee.library.lending.patron.application.usecase

import arrow.core.Either
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookCheckingOutFailedEvent
import io.chrislee.library.lending.patron.application.domain.CheckoutDuration
import io.chrislee.library.lending.patron.application.domain.PatronId
import java.time.Instant

internal interface CheckOutBookOnHoldUseCase {
    suspend fun execute(command: CheckOutBookOnHoldCommand): Either<BookCheckingOutFailedEvent, BookCheckedOutEvent>
}

internal data class CheckOutBookOnHoldCommand(
    val patronId: PatronId,
    val bookId: BookId,
    val checkoutDuration: CheckoutDuration,
    val occurredAt: Instant = Instant.now(),
)
