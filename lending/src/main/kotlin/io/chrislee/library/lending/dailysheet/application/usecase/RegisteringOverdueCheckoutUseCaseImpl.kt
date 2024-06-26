package io.chrislee.library.lending.dailysheet.application.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.dailysheet.application.domain.DailySheet
import io.chrislee.library.lending.patron.application.domain.PatronRepository

internal class RegisteringOverdueCheckoutUseCaseImpl(
    private val dailySheet: DailySheet,
    private val patronRepository: PatronRepository,
) : RegisteringOverdueCheckoutUseCase {
    override suspend fun registerOverdueCheckouts(): Either<SystemError, List<RegisteringOverdueCheckoutUseCaseResult>> {
        return either {
            dailySheet.queryForCheckoutsToOverdue().bind().toEvents().map { event ->
                either {
                    val patron = patronRepository.findByPatronId(event.patronId).bind()
                        .getOrElse { raise(SystemError.DataInconsistency("고객이 존재하지 않습니다: $event")) }
                    patronRepository.save(patron, event).bind()
                    event
                }.fold(
                    ifLeft = { RegisteringOverdueCheckoutUseCaseResult.Failure(event, it) },
                    ifRight = { RegisteringOverdueCheckoutUseCaseResult.Success(event) },
                )
            }
        }
    }
}
