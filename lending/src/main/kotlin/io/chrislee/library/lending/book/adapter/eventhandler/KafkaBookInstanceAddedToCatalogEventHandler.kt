package io.chrislee.library.lending.book.adapter.eventhandler

import arrow.core.raise.ensure
import io.chrislee.library.catalog.v1.Catalog
import io.chrislee.library.catalog.v1.Catalog.BookInstanceAddedToCatalogue
import io.chrislee.library.common.infrastructure.transactionalEither
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.book.application.usecase.CreateAvailableBookCommand
import io.chrislee.library.lending.book.application.usecase.CreateAvailableBookUseCase
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.transaction.reactive.TransactionalOperator

internal class KafkaBookInstanceAddedToCatalogEventHandler(
    private val usecase: CreateAvailableBookUseCase,
    private val transactionalOperator: TransactionalOperator,
) {
    @KafkaListener(
        topics = ["\${spring.kafka.topic.catalog-bookinstanceadded}"],
        groupId = "KafkaBookInstanceAddedToCatalogEventHandler",
    )
    suspend fun listen(
        message: ByteArray,
        ack: Acknowledgment,
    ) {
        transactionalEither(transactionalOperator) {
            val parsed = BookInstanceAddedToCatalogue.parseFrom(message)
            ensure(parsed.bookId.isNotBlank()) {
                "도서 ID가 비어 있습니다: (ID: ${parsed.bookId}, BookType: ${parsed.bookType})"
            }
            val bookId = BookId.from(parsed.bookId).mapLeft { it.message }.bind()
            val bookType = when (parsed.bookType) {
                Catalog.BookType.BOOK_TYPE_RESTRICTED -> BookType.Restricted
                Catalog.BookType.BOOK_TYPE_CIRCULATING -> BookType.Circulating
                else -> raise("도서 유형이 비정상입니다: ${parsed.bookType}")
            }
            val availableBook = usecase.execute(CreateAvailableBookCommand(bookId, bookType))
                .mapLeft { it.message }.bind()
            ack.acknowledge()
            availableBook
        }
            .onLeft {
                logger.error { "이벤트 처리 중 오류 발생: $it" }
                // TODO deadletter processing
                ack.acknowledge()
            }
            .onRight { logger.info { "이벤트 처리 완료: $it" } }
    }

    companion object {
        private val logger = mu.KotlinLogging.logger { }
    }
}
