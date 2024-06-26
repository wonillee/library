package io.chrislee.library.lending

import io.chrislee.library.common.infrastructure.DomainEventPublisherConfiguration
import io.chrislee.library.common.infrastructure.KafkaConfiguration
import io.chrislee.library.lending.book.adapter.BookConfiguration
import io.chrislee.library.lending.dailysheet.adapter.DailySheetConfiguration
import io.chrislee.library.lending.patron.adapter.PatronConfiguration
import io.chrislee.library.lending.patronprofile.adapter.PatronProfileConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableScheduling
@Import(
    BookConfiguration::class,
    DailySheetConfiguration::class,
    PatronConfiguration::class,
    PatronProfileConfiguration::class,
    DomainEventPublisherConfiguration::class,
    KafkaConfiguration::class,
)
class LendingConfiguration
