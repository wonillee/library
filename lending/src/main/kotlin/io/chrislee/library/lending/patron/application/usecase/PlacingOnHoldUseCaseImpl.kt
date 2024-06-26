package io.chrislee.library.lending.patron.application.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.chrislee.library.lending.patron.application.domain.BookHoldFailedEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvents
import io.chrislee.library.lending.patron.application.domain.PatronRepository

internal class PlacingOnHoldUseCaseImpl(
    private val query: FindAvailableBookQuery,
    private val patronRepository: PatronRepository,
) : PlacingOnHoldUseCase {
    override suspend fun execute(command: PlacingOnHoldCommand): Either<BookHoldFailedEvent, BookPlacedOnHoldEvents> {
        return either {
            val book = query.findAvailableBookByBookId(command.bookId)
                .mapLeft { failed(command, "대여 가능 도서 질의 수행 중 시스템 오류가 발생했습니다: ${it.message}") }.bind()
                .getOrElse { raise(failed(command, "질의 수행 결과 해당하는 대여 가능 도서를 찾을 수 없습니다: $command")) }
            val patron = patronRepository.findByPatronId(command.patronId)
                .mapLeft { failed(command, "고객 조회 중 시스템 오류가 발생했습니다: ${it.message}") }.bind()
                .getOrElse { raise(failed(command, "고객 조회 결과 해당하는 ID의 고객이 존재하지 않습니다: $command")) }
            val event = patron.placeOnHold(book, command.holdDuration).bind()
            patronRepository.save(patron, event.bookPlacedOnHoldEvent)
                .mapLeft { raise(failed(command, "고객 대여 예약 상태 변경 중 시스템 오류가 발생했습니다: ${it.message}")) }
                .bind()
            event
        }
    }

    private fun failed(command: PlacingOnHoldCommand, reason: String): BookHoldFailedEvent {
        return BookHoldFailedEvent(
            patronId = command.patronId,
            bookId = command.bookId,
            reason = reason,
        )
    }
}
