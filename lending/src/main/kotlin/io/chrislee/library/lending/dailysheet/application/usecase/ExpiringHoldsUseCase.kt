package io.chrislee.library.lending.dailysheet.application.usecase

import arrow.core.Either
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent

internal interface ExpiringHoldsUseCase {
    suspend fun expireHolds(): Either<SystemError, List<ExpiringHoldsUseCaseResult>>
}

internal sealed class ExpiringHoldsUseCaseResult {
    data class Success(val event: BookHoldExpiredEvent) : ExpiringHoldsUseCaseResult()

    data class Failure(
        val eventFailedToEmit: BookHoldExpiredEvent,
        val systemError: SystemError,
    ) : ExpiringHoldsUseCaseResult()
}
