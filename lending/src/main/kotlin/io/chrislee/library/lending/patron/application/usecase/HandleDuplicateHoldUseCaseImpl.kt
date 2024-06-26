package io.chrislee.library.lending.patron.application.usecase

import arrow.core.Either
import io.chrislee.library.lending.book.application.domain.BookDuplicateHoldFoundEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancellingFailedEvent

internal class HandleDuplicateHoldUseCaseImpl(
    private val useCase: CancelHoldUseCase,
) : HandleDuplicateHoldUseCase {
    override suspend fun handle(
        event: BookDuplicateHoldFoundEvent,
    ): Either<BookHoldCancellingFailedEvent, BookHoldCancelledEvent> {
        val command = CancelHoldCommand(
            patronId = event.secondPatronId,
            bookId = event.bookId,
        )
        return useCase.execute(command)
    }
}
