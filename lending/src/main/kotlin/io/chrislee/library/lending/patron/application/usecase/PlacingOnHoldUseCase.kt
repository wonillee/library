package io.chrislee.library.lending.patron.application.usecase

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.BookHoldFailedEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvents
import io.chrislee.library.lending.patron.application.domain.HoldDuration
import io.chrislee.library.lending.patron.application.domain.NumberOfDays
import io.chrislee.library.lending.patron.application.domain.PatronId
import java.time.Instant

internal interface PlacingOnHoldUseCase {
    suspend fun execute(command: PlacingOnHoldCommand): Either<BookHoldFailedEvent, BookPlacedOnHoldEvents>
}

internal data class PlacingOnHoldCommand(
    val patronId: PatronId,
    val bookId: BookId,
    val numberOfDays: Option<NumberOfDays>,
    val occurredAt: Instant = Instant.now(),
) {
    val holdDuration: HoldDuration = when (numberOfDays) {
        None -> HoldDuration.OpenEnded(occurredAt)
        is Some -> HoldDuration.CloseEnded.of(occurredAt, numberOfDays.value)
    }
}
