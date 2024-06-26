package io.chrislee.library.catalog.adapter

import io.chrislee.library.catalog.adapter.persistence.CatalogDatabase
import io.chrislee.library.catalog.adapter.service.CatalogServiceImpl
import io.chrislee.library.catalog.application.domain.CatalogService
import io.chrislee.library.common.infrastructure.KafkaConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.transaction.reactive.TransactionalOperator

@Configuration
@EnableAutoConfiguration
@ComponentScan
@Import(KafkaConfiguration::class)
internal class CatalogConfiguration {
    @Value("\${spring.kafka.topic.catalog-bookinstanceadded}")
    private lateinit var topicName: String

    @Bean
    fun catalogService(
        catalogDatabase: CatalogDatabase,
        transactionalOperator: TransactionalOperator,
        kafkaTemplate: KafkaTemplate<String, ByteArray>,
    ): CatalogService {
        return CatalogServiceImpl(catalogDatabase, transactionalOperator, kafkaTemplate, topicName)
    }

    @Bean
    fun catalogDatabase(databaseClient: DatabaseClient): CatalogDatabase {
        return CatalogDatabase(databaseClient)
    }
}
