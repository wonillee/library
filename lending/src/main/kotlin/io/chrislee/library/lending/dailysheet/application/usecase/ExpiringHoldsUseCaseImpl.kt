package io.chrislee.library.lending.dailysheet.application.usecase

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.dailysheet.application.domain.DailySheet
import io.chrislee.library.lending.patron.application.domain.PatronRepository

internal class ExpiringHoldsUseCaseImpl(
    private val dailySheet: DailySheet,
    private val patronRepository: PatronRepository,
) : ExpiringHoldsUseCase {
    override suspend fun expireHolds(): Either<SystemError, List<ExpiringHoldsUseCaseResult>> {
        return either {
            dailySheet.queryForHoldsToExpireSheet().bind().toEvents().map { event ->
                either {
                    val patron = patronRepository.findByPatronId(event.patronId).bind()
                        .getOrElse { raise(SystemError.DataInconsistency("고객이 존재하지 않습니다: $event")) }
                    patronRepository.save(patron, event).bind()
                    event
                }.fold(
                    ifLeft = { ExpiringHoldsUseCaseResult.Failure(event, it) },
                    ifRight = { ExpiringHoldsUseCaseResult.Success(event) },
                )
            }
        }
    }
}
