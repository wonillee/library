package io.chrislee.library.lending.book.adapter

import io.chrislee.library.common.domain.DomainEventPublisher
import io.chrislee.library.lending.book.adapter.eventhandler.KafkaBookInstanceAddedToCatalogEventHandler
import io.chrislee.library.lending.book.adapter.eventhandler.PatronEventHandler
import io.chrislee.library.lending.book.adapter.persistence.DatabaseBookRepository
import io.chrislee.library.lending.book.adapter.persistence.R2DBCBookRepository
import io.chrislee.library.lending.book.application.domain.BookRepository
import io.chrislee.library.lending.book.application.usecase.CreateAvailableBookUseCase
import io.chrislee.library.lending.book.application.usecase.CreateAvailableBookUseCaseImpl
import io.chrislee.library.lending.book.application.usecase.PatronEventFiredUseCasesImpl
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories
import org.springframework.transaction.reactive.TransactionalOperator

@EnableR2dbcRepositories(basePackages = ["io.chrislee.library.lending.book.adapter.persistence"])
@Configuration
internal class BookConfiguration {
    @Bean
    fun bookRepository(springDataImpl: R2DBCBookRepository): BookRepository {
        return DatabaseBookRepository(springDataImpl)
    }

    @Bean
    fun patronEventHandler(
        transactionalOperator: TransactionalOperator,
        domainEventPublisher: DomainEventPublisher,
        bookRepository: BookRepository,
    ): PatronEventHandler {
        val usecasesImpl = PatronEventFiredUseCasesImpl(bookRepository)
        return PatronEventHandler(
            transactionalOperator,
            domainEventPublisher,
            usecasesImpl,
            usecasesImpl,
            usecasesImpl,
            usecasesImpl,
            usecasesImpl,
        )
    }

    @Bean
    fun kafkaEventHandler(
        usecase: CreateAvailableBookUseCase,
        transactionalOperator: TransactionalOperator,
    ): KafkaBookInstanceAddedToCatalogEventHandler {
        return KafkaBookInstanceAddedToCatalogEventHandler(usecase, transactionalOperator)
    }

    @Bean
    fun createAvailableBookUseCase(bookRepository: BookRepository): CreateAvailableBookUseCase {
        return CreateAvailableBookUseCaseImpl(bookRepository)
    }
}
