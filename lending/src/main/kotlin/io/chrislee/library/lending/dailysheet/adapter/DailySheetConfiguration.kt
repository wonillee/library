package io.chrislee.library.lending.dailysheet.adapter

import io.chrislee.library.common.domain.DomainEventPublisher
import io.chrislee.library.lending.dailysheet.adapter.batch.ExpiringHoldsScheduler
import io.chrislee.library.lending.dailysheet.adapter.batch.RegisteringOverdueCheckoutScheduler
import io.chrislee.library.lending.dailysheet.adapter.persistence.DatabaseDailySheetReadModel
import io.chrislee.library.lending.dailysheet.application.domain.DailySheet
import io.chrislee.library.lending.dailysheet.application.usecase.ExpiringHoldsUseCase
import io.chrislee.library.lending.dailysheet.application.usecase.ExpiringHoldsUseCaseImpl
import io.chrislee.library.lending.dailysheet.application.usecase.RegisteringOverdueCheckoutUseCase
import io.chrislee.library.lending.dailysheet.application.usecase.RegisteringOverdueCheckoutUseCaseImpl
import io.chrislee.library.lending.patron.application.domain.PatronRepository
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator
import java.time.Clock

@Configuration
internal class DailySheetConfiguration {
    @Bean
    fun dailySheet(databaseClient: DatabaseClient): DailySheet {
        return DatabaseDailySheetReadModel(databaseClient, Clock.systemDefaultZone())
    }

    @Bean
    fun expiringHoldsUseCase(dailySheet: DailySheet, patronRepository: PatronRepository): ExpiringHoldsUseCase {
        return ExpiringHoldsUseCaseImpl(dailySheet, patronRepository)
    }

    @Bean
    fun registeringOverdueCheckoutUseCase(
        dailySheet: DailySheet,
        patronRepository: PatronRepository,
    ): RegisteringOverdueCheckoutUseCase {
        return RegisteringOverdueCheckoutUseCaseImpl(dailySheet, patronRepository)
    }

    @Bean
    fun expiringHoldsScheduler(
        useCase: ExpiringHoldsUseCase,
        transactionalOperator: TransactionalOperator,
        domainEventPublisher: DomainEventPublisher,
    ): ExpiringHoldsScheduler {
        return ExpiringHoldsScheduler(useCase, transactionalOperator, domainEventPublisher)
    }

    @Bean
    fun registeringOverdueCheckoutScheduler(
        useCase: RegisteringOverdueCheckoutUseCase,
        patronRepository: PatronRepository,
        transactionalOperator: TransactionalOperator,
        domainEventPublisher: DomainEventPublisher,
    ): RegisteringOverdueCheckoutScheduler {
        return RegisteringOverdueCheckoutScheduler(
            useCase,
            transactionalOperator,
            domainEventPublisher,
        )
    }
}
