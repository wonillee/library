package io.chrislee.library.lending.dailysheet.adapter.batch

import io.chrislee.library.common.domain.DomainEventPublisher
import io.chrislee.library.common.infrastructure.transactionalEither
import io.chrislee.library.lending.dailysheet.application.usecase.RegisteringOverdueCheckoutUseCase
import io.chrislee.library.lending.dailysheet.application.usecase.RegisteringOverdueCheckoutUseCaseResult
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.reactive.TransactionalOperator

// TODO use Spring Batch
internal class RegisteringOverdueCheckoutScheduler(
    private val useCase: RegisteringOverdueCheckoutUseCase,
    private val transactionalOperator: TransactionalOperator,
    private val domainEventPublisher: DomainEventPublisher,
) {
    @Scheduled(cron = "0 0 0 * * ?", zone = "UTC")
    suspend fun execute() {
        useCase.registerOverdueCheckouts()
            .onLeft { "대여 만기 도서 조회 실패: ${it.message}" }
            .onRight { results ->
                results.forEach { result ->
                    transactionalEither(transactionalOperator) {
                        when (result) {
                            is RegisteringOverdueCheckoutUseCaseResult.Failure -> raise(result.systemError)
                            is RegisteringOverdueCheckoutUseCaseResult.Success ->
                                domainEventPublisher.publish(result.event).bind()
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
