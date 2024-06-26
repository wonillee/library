package io.chrislee.library.lending.book.adapter.eventhandler

import io.chrislee.library.catalog.v1.Catalog
import io.chrislee.library.common.domain.DomainEventId
import io.chrislee.library.common.infrastructure.DomainEventPublisherConfiguration
import io.chrislee.library.common.infrastructure.KafkaConfiguration
import io.chrislee.library.common.infrastructure.TestContainerDatabaseInitializer
import io.chrislee.library.common.infrastructure.TestContainerKafkaInitializer
import io.chrislee.library.lending.book.adapter.BookConfiguration
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookRepository
import io.chrislee.library.lending.book.application.domain.BookType
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.ContextConfiguration
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.time.Instant

@Disabled("테스트 수행 시간이 길어서 Kafka 연동 테스트 생략")
@DataR2dbcTest(properties = ["spring.kafka.topic.catalog-bookinstanceadded=catalog-bookinstanceadded"])
@ContextConfiguration(
    classes = [
        BookConfiguration::class,
        DomainEventPublisherConfiguration::class,
        KafkaConfiguration::class,
    ],
    initializers = [
        TestContainerKafkaInitializer::class,
        TestContainerDatabaseInitializer::class,
    ],
)
class KafkaBookInstanceAddedToCatalogEventHandlerIT {
    @Autowired
    private lateinit var kafkaTemplate: KafkaTemplate<String, ByteArray>

    @Autowired
    private lateinit var bookRepository: BookRepository

    @DisplayName("catalog 서비스에서 새로운 도서 인스턴스가 생성되었다는 이벤트를 수신하여 lending 서비스 내에 대여 가능한 도서를 추가한다")
    @Test
    fun testBasic() {
        runTest {
            val bookId = BookId.uniqueOne()
            val event = Catalog.BookInstanceAddedToCatalogue.newBuilder()
                .setEventId(DomainEventId.uniqueOne().source)
                .setIsbn("ISBN")
                .setBookId(bookId.source)
                .setBookType(Catalog.BookType.BOOK_TYPE_CIRCULATING)
                .setInstant(Instant.now().epochSecond)
                .build()

            kafkaTemplate.send("catalog-bookinstanceadded", event.toByteArray()).await()
            withContext(Dispatchers.IO) { Thread.sleep(500) }
            val book = bookRepository.findByBookId(bookId).shouldBeRight().shouldBeSome()
            expectThat(book).isA<AvailableBook>().and {
                get { this.bookId }.isEqualTo(bookId)
                get { this.bookType }.isEqualTo(BookType.Circulating)
            }
        }
    }
}
