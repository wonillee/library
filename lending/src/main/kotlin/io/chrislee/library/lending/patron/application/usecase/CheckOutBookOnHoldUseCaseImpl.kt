package io.chrislee.library.lending.patron.application.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookCheckingOutFailedEvent
import io.chrislee.library.lending.patron.application.domain.PatronRepository

internal class CheckOutBookOnHoldUseCaseImpl(
    private val query: FindBookOnHoldQuery,
    private val patronRepository: PatronRepository,
) : CheckOutBookOnHoldUseCase {
    override suspend fun execute(
        command: CheckOutBookOnHoldCommand,
    ): Either<BookCheckingOutFailedEvent, BookCheckedOutEvent> {
        return either {
            val bookOnHold = query.findBookOnHold(command.bookId, command.patronId)
                .mapLeft { failedBySystem(command, it) }.bind()
                .getOrElse { raise(failed(command, "존재하지 않는 도서입니다: $command")) }
            val patron = patronRepository.findByPatronId(command.patronId)
                .mapLeft { failedBySystem(command, it) }.bind()
                .getOrElse { raise(failed(command, "존재하지 않는 고객입니다: $command")) }
            val event = patron.checkOut(bookOnHold, command.checkoutDuration).bind()
            patronRepository.save(patron, event)
                .mapLeft { failedBySystem(command, it) }.bind()
            event
        }
    }

    private fun failed(command: CheckOutBookOnHoldCommand, reason: String): BookCheckingOutFailedEvent {
        return BookCheckingOutFailedEvent(
            patronId = command.patronId,
            bookId = command.bookId,
            reason = reason,
        )
    }

    private fun failedBySystem(command: CheckOutBookOnHoldCommand, error: SystemError): BookCheckingOutFailedEvent {
        return BookCheckingOutFailedEvent(
            patronId = command.patronId,
            bookId = command.bookId,
            reason = "시스템 오류가 발생했습니다: ${error.message}",
        )
    }
}
