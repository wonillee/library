package io.chrislee.library.lending.dailysheet.adapter.batch

import io.chrislee.library.common.domain.DomainEventPublisher
import io.chrislee.library.common.infrastructure.transactionalEither
import io.chrislee.library.lending.dailysheet.application.usecase.ExpiringHoldsUseCase
import io.chrislee.library.lending.dailysheet.application.usecase.ExpiringHoldsUseCaseResult
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.reactive.TransactionalOperator

// TODO use Spring Batch
internal class ExpiringHoldsScheduler(
    private val useCase: ExpiringHoldsUseCase,
    private val transactionalOperator: TransactionalOperator,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Scheduled(cron = "0 0 0 * * ?", zone = "UTC")
    suspend fun execute() {
        useCase.expireHolds()
            .onLeft { logger.error { "대여 예약 만기 도서 조회 실패: ${it.message}" } }
            .onRight { results ->
                results.forEach { result ->
                    transactionalEither(transactionalOperator) {
                        when (result) {
                            is ExpiringHoldsUseCaseResult.Failure -> raise(result.systemError)
                            is ExpiringHoldsUseCaseResult.Success -> domainEventPublisher.publish(result.event).bind()
                        }
                    }
                        .onLeft { logger.error { "이벤트 처리 실패: $it" } }
                }
            }
    }

    companion object {
        private val logger = mu.KotlinLogging.logger { }
    }
}
