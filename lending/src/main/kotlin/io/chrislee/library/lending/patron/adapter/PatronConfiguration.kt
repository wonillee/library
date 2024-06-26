package io.chrislee.library.lending.patron.adapter

import io.chrislee.library.lending.book.adapter.persistence.DatabaseBookRepository
import io.chrislee.library.lending.book.adapter.persistence.R2DBCBookRepository
import io.chrislee.library.lending.patron.adapter.persistence.DatabasePatronRepository
import io.chrislee.library.lending.patron.adapter.persistence.R2DBCHoldRepository
import io.chrislee.library.lending.patron.adapter.persistence.R2DBCOverdueCheckoutRepository
import io.chrislee.library.lending.patron.adapter.persistence.R2DBCPatronRepository
import io.chrislee.library.lending.patron.application.domain.DefaultPlacingOnHoldPoliciesProvider
import io.chrislee.library.lending.patron.application.domain.PatronRepository
import io.chrislee.library.lending.patron.application.domain.PlacingOnHoldPoliciesProvider
import io.chrislee.library.lending.patron.application.usecase.CancelHoldUseCase
import io.chrislee.library.lending.patron.application.usecase.CancelHoldUseCaseImpl
import io.chrislee.library.lending.patron.application.usecase.CheckOutBookOnHoldUseCase
import io.chrislee.library.lending.patron.application.usecase.CheckOutBookOnHoldUseCaseImpl
import io.chrislee.library.lending.patron.application.usecase.FindAvailableBookQuery
import io.chrislee.library.lending.patron.application.usecase.FindBookOnHoldQuery
import io.chrislee.library.lending.patron.application.usecase.HandleDuplicateHoldUseCase
import io.chrislee.library.lending.patron.application.usecase.HandleDuplicateHoldUseCaseImpl
import io.chrislee.library.lending.patron.application.usecase.PlacingOnHoldUseCase
import io.chrislee.library.lending.patron.application.usecase.PlacingOnHoldUseCaseImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.r2dbc.core.DatabaseClient

@EnableR2dbcRepositories(basePackages = ["io.chrislee.library.lending.patron.adapter.persistence"])
@ComponentScan(basePackages = ["io.chrislee.library.lending.patron.adapter.web"])
@Configuration
internal class PatronConfiguration {
    @Bean
    fun databasePatronRepository(
        databaseClient: DatabaseClient,
        r2DBCPatronRepository: R2DBCPatronRepository,
        r2DBCHoldRepository: R2DBCHoldRepository,
        r2DBCOverdueCheckoutRepository: R2DBCOverdueCheckoutRepository,
        placingOnHoldPoliciesProvider: PlacingOnHoldPoliciesProvider,
    ): PatronRepository {
        return DatabasePatronRepository(
            databaseClient,
            r2DBCPatronRepository,
            r2DBCHoldRepository,
            r2DBCOverdueCheckoutRepository,
            placingOnHoldPoliciesProvider,
        )
    }

    @Bean
    fun dateabaseBookRepository(
        r2DBCBookRepository: R2DBCBookRepository,
    ): DatabaseBookRepository {
        return DatabaseBookRepository(r2DBCBookRepository)
    }

    @Bean
    fun placingOnHoldPoliciesProvider(): PlacingOnHoldPoliciesProvider {
        return DefaultPlacingOnHoldPoliciesProvider()
    }

    @Bean
    fun findAvailableBookQuery(bookRepository: DatabaseBookRepository): FindAvailableBookQuery {
        return bookRepository
    }

    @Bean
    fun findBookOnHoldQuery(bookRepository: DatabaseBookRepository): FindBookOnHoldQuery {
        return bookRepository
    }

    @Bean
    fun cancelHoldUseCase(
        findBookOnHoldQuery: FindBookOnHoldQuery,
        patronRepository: PatronRepository,
    ): CancelHoldUseCase {
        return CancelHoldUseCaseImpl(findBookOnHoldQuery, patronRepository)
    }

    @Bean
    fun checkOutBookOnHoldUseCase(
        findBookOnHoldQuery: FindBookOnHoldQuery,
        patronRepository: PatronRepository,
    ): CheckOutBookOnHoldUseCase {
        return CheckOutBookOnHoldUseCaseImpl(findBookOnHoldQuery, patronRepository)
    }

    @Bean
    fun handleDuplicateHoldUseCase(
        cancelHoldUseCase: CancelHoldUseCase,
    ): HandleDuplicateHoldUseCase {
        return HandleDuplicateHoldUseCaseImpl(cancelHoldUseCase)
    }

    @Bean
    fun placingOnHoldUseCase(
        findAvailableBookQuery: FindAvailableBookQuery,
        patronRepository: PatronRepository,
    ): PlacingOnHoldUseCase {
        return PlacingOnHoldUseCaseImpl(findAvailableBookQuery, patronRepository)
    }
}
