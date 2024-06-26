package io.chrislee.library.lending.patronprofile.adapter.web

import arrow.core.None
import arrow.core.Some
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.toOption
import io.chrislee.library.common.domain.DomainEventPublisher
import io.chrislee.library.common.infrastructure.ApiResponse
import io.chrislee.library.common.infrastructure.transactionalEither
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.BookHoldCancellingFailedEvent
import io.chrislee.library.lending.patron.application.domain.NumberOfDays
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.chrislee.library.lending.patron.application.usecase.CancelHoldCommand
import io.chrislee.library.lending.patron.application.usecase.CancelHoldUseCase
import io.chrislee.library.lending.patron.application.usecase.PlacingOnHoldCommand
import io.chrislee.library.lending.patron.application.usecase.PlacingOnHoldUseCase
import io.chrislee.library.lending.patronprofile.application.domain.Checkout
import io.chrislee.library.lending.patronprofile.application.domain.Hold
import io.chrislee.library.lending.patronprofile.application.domain.PatronProfile
import io.chrislee.library.lending.patronprofile.application.domain.PatronProfileRepository
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
internal class PatronProfileController(
    private val patronProfileRepository: PatronProfileRepository,
    private val placingOnHoldUseCase: PlacingOnHoldUseCase,
    private val cancelHoldUseCase: CancelHoldUseCase,
    private val domainEventPublisher: DomainEventPublisher,
    private val transactionalOperator: TransactionalOperator,
) {
    @GetMapping("/profiles/{patronId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun patronProfile(
        @PathVariable(name = "patronId") patronIdInput: String,
    ): ResponseEntity<ApiResponse<PatronProfile>> {
        val patronId = PatronId.from(patronIdInput).getOrElse { return ApiResponse.badRequest("고객 ID 입력 오류입니다.") }
        return either { patronProfileRepository.findByPatronId(patronId).bind() }
            .fold(
                ifLeft = { (ApiResponse.serverError(it.message)) },
                ifRight = { ApiResponse.ok(it) },
            )
    }

    @GetMapping("/profiles/{patronId}/holds", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun findHolds(
        @PathVariable(name = "patronId") patronIdInput: String,
    ): ResponseEntity<ApiResponse<List<Hold>>> {
        val patronId = PatronId.from(patronIdInput).getOrElse { return ApiResponse.badRequest("고객 ID 입력 오류입니다.") }
        return either { patronProfileRepository.findByPatronId(patronId).bind().holdsView.source.values }
            .fold(
                ifLeft = { ApiResponse.serverError() },
                ifRight = { ApiResponse.ok(it.toList()) },
            )
    }

    @GetMapping("/profiles/{patronId}/holds/{bookId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun findHold(
        @PathVariable(name = "patronId") patronIdInput: String,
        @PathVariable(name = "bookId") bookIdInput: String,
    ): ResponseEntity<ApiResponse<Hold>> {
        val patronId = PatronId.from(patronIdInput).getOrElse { return ApiResponse.badRequest("고객 ID 입력 오류입니다.") }
        val bookId = BookId.from(bookIdInput).getOrElse { return ApiResponse.badRequest("도서 ID 입력 오류입니다.") }
        return either { patronProfileRepository.findByPatronId(patronId).bind().findHold(bookId) }
            .fold(
                ifLeft = { ApiResponse.serverError() },
                ifRight = {
                    when (it) {
                        None -> ApiResponse.notFound("예약한 도서가 없습니다.")
                        is Some -> ApiResponse.ok(it.value)
                    }
                },
            )
    }

    @GetMapping("/profiles/{patronId}/checkouts", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun findCheckouts(
        @PathVariable(name = "patronId") patronIdInput: String,
    ): ResponseEntity<ApiResponse<List<Checkout>>> {
        val patronId = PatronId.from(patronIdInput).getOrElse { return ApiResponse.badRequest("고객 ID 입력 오류입니다.") }
        return either { patronProfileRepository.findByPatronId(patronId).bind().checkoutsView.source.values }
            .fold(
                ifLeft = { ApiResponse.serverError() },
                ifRight = { ApiResponse.ok(it.toList()) },
            )
    }

    @GetMapping("/profiles/{patronId}/checkouts/{bookId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun findCheckout(
        @PathVariable(name = "patronId") patronIdInput: String,
        @PathVariable(name = "bookId") bookIdInput: String,
    ): ResponseEntity<ApiResponse<Checkout>> {
        val patronId = PatronId.from(patronIdInput).getOrElse { return ApiResponse.badRequest("고객 ID 입력 오류입니다.") }
        val bookId = BookId.from(bookIdInput).getOrElse { return ApiResponse.badRequest("도서 ID 입력 오류입니다.") }
        return either { patronProfileRepository.findByPatronId(patronId).bind().findCheckout(bookId) }
            .fold(
                ifLeft = { ApiResponse.serverError() },
                ifRight = {
                    when (it) {
                        None -> ApiResponse.notFound("대여한 도서가 없습니다.")
                        is Some -> ApiResponse.ok(it.value)
                    }
                },
            )
    }

    @PostMapping(
        "/profiles/{patronId}/holds",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun placeHold(
        @PathVariable(name = "patronId") patronIdInput: String,
        @RequestBody request: PlaceHoldRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val patronId = PatronId.from(patronIdInput).getOrElse { return ApiResponse.badRequest("고객 ID 입력 오류입니다.") }
        val bookId = BookId.from(request.bookId).getOrElse { return ApiResponse.badRequest("도서 ID 입력 오류입니다.") }
        val numberOfDays = request.numberOfDays.toOption()
            .map { NumberOfDays.of(it).getOrElse { return ApiResponse.badRequest("대여일 입력 오류입니다.") } }
        return transactionalEither(transactionalOperator) {
            val events = placingOnHoldUseCase.execute(
                PlacingOnHoldCommand(
                    patronId = patronId,
                    bookId = bookId,
                    numberOfDays = numberOfDays,
                ),
            ).bind()
            domainEventPublisher.publish(events.bookPlacedOnHoldEvent).bind()
            // 실패해도 전체 트랜잭션에 무관한 이벤트이므로 raise하지 않는다
            events.maximumNumberOnHoldsReachedEvent.onSome(domainEventPublisher::publish)
        }
            .fold(
                ifLeft = { ApiResponse.serverError() },
                ifRight = { ApiResponse.ok() },
            )
    }

    @DeleteMapping("/profiles/{patronId}/holds/{bookId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun cancelHold(
        @PathVariable(name = "patronId") patronIdInput: String,
        @PathVariable(name = "bookId") bookIdInput: String,
    ): ResponseEntity<ApiResponse<Any>> {
        val patronId = PatronId.from(patronIdInput).getOrElse { return ApiResponse.badRequest("고객 ID 입력 오류입니다.") }
        val bookId = BookId.from(bookIdInput).getOrElse { return ApiResponse.badRequest("도서 ID 입력 오류입니다.") }
        return either {
            val event = cancelHoldUseCase.execute(
                CancelHoldCommand(
                    patronId = patronId,
                    bookId = bookId,
                ),
            ).bind()
            domainEventPublisher.publish(event)
                .mapLeft {
                    BookHoldCancellingFailedEvent(
                        patronId = patronId,
                        bookId = bookId,
                        reason = BookHoldCancellingFailedEvent.Reason.System,
                        details = "도메인 이벤트 전송 중 실패 발생: $event",
                    )
                }
                .bind()
        }
            .fold(
                ifLeft = {
                    when (it.reason) {
                        BookHoldCancellingFailedEvent.Reason.BookNotFound -> ApiResponse.notFound("도서를 찾을 수 없습니다.")
                        BookHoldCancellingFailedEvent.Reason.PatronNotFound -> ApiResponse.notFound("고객을 찾을 수 없습니다.")
                        BookHoldCancellingFailedEvent.Reason.BookNotHeld ->
                            ApiResponse.badRequest("대여 예약한 도서만 취소할 수 있습니다.")

                        BookHoldCancellingFailedEvent.Reason.System -> ApiResponse.serverError()
                    }
                },
                ifRight = { ApiResponse.ok() },
            )
    }
}

internal data class PlaceHoldRequest(
    val bookId: String,
    val numberOfDays: Int?,
)
