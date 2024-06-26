package io.chrislee.library.lending.dailysheet.application.usecase

import arrow.core.Either
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.patron.application.domain.OverdueCheckoutRegisteredEvent

internal interface RegisteringOverdueCheckoutUseCase {
    suspend fun registerOverdueCheckouts(): Either<SystemError, List<RegisteringOverdueCheckoutUseCaseResult>>
}

internal sealed class RegisteringOverdueCheckoutUseCaseResult {
    data class Success(val event: OverdueCheckoutRegisteredEvent) : RegisteringOverdueCheckoutUseCaseResult()

    data class Failure(
        val eventFailedToEmit: OverdueCheckoutRegisteredEvent,
        val systemError: SystemError,
    ) : RegisteringOverdueCheckoutUseCaseResult()
}
