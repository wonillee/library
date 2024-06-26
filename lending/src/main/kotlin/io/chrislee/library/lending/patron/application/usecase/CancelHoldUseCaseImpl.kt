package io.chrislee.library.lending.patron.application.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancellingFailedEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancellingFailedEvent.Reason.BookNotFound
import io.chrislee.library.lending.patron.application.domain.BookHoldCancellingFailedEvent.Reason.PatronNotFound
import io.chrislee.library.lending.patron.application.domain.BookHoldCancellingFailedEvent.Reason.System
import io.chrislee.library.lending.patron.application.domain.PatronRepository

internal class CancelHoldUseCaseImpl(
    private val query: FindBookOnHoldQuery,
    private val patronRepository: PatronRepository,
) : CancelHoldUseCase {
    override suspend fun execute(
        command: CancelHoldCommand,
    ): Either<BookHoldCancellingFailedEvent, BookHoldCancelledEvent> {
        return either {
            val bookOnHold = query.findBookOnHold(command.bookId, command.patronId)
                .mapLeft { failed(command, System, "대여 예약 도서 질의 수행 중 시스템 오류가 발생했습니다: ${it.message}") }.bind()
                .getOrElse { raise(failed(command, BookNotFound, "질의 수행 결과 해당하는 대여 예약 도서를 찾을 수 없습니다: $command")) }
            val patron = patronRepository.findByPatronId(command.patronId)
                .mapLeft { failed(command, System, "고객 조회 중 시스템 오류가 발생했습니다: ${it.message}") }.bind()
                .getOrElse { raise(failed(command, PatronNotFound, "고객 조회 결과 해당하는 ID의 고객이 존재하지 않습니다: $command")) }
            val cancelledEvent = patron.cancelHold(bookOnHold).bind()
            patronRepository.save(patron, cancelledEvent)
                .mapLeft { raise(failed(command, System, "고객 대여 예약 상태 변경 중 시스템 오류가 발생했습니다: ${it.message}")) }
                .bind()
            cancelledEvent
        }
    }

    private fun failed(
        command: CancelHoldCommand,
        reason: BookHoldCancellingFailedEvent.Reason,
        details: String,
    ): BookHoldCancellingFailedEvent {
        return BookHoldCancellingFailedEvent(
            patronId = command.patronId,
            bookId = command.bookId,
            reason = reason,
            details = details,
        )
    }
}
