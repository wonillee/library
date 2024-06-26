package io.chrislee.library.catalog.adapter.service

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import io.chrislee.library.catalog.adapter.persistence.CatalogDatabase
import io.chrislee.library.catalog.application.domain.Book
import io.chrislee.library.catalog.application.domain.BookAuthor
import io.chrislee.library.catalog.application.domain.BookId
import io.chrislee.library.catalog.application.domain.BookInstance
import io.chrislee.library.catalog.application.domain.BookTitle
import io.chrislee.library.catalog.application.domain.BookType
import io.chrislee.library.catalog.application.domain.CatalogError
import io.chrislee.library.catalog.application.domain.CatalogService
import io.chrislee.library.catalog.application.domain.ISBN
import io.chrislee.library.catalog.v1.Catalog.BookInstanceAddedToCatalogue
import io.chrislee.library.catalog.v1.Catalog.BookType.BOOK_TYPE_CIRCULATING
import io.chrislee.library.catalog.v1.Catalog.BookType.BOOK_TYPE_RESTRICTED
import io.chrislee.library.common.domain.DomainEventId
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.common.infrastructure.transactionalEither
import kotlinx.coroutines.future.await
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.transaction.reactive.TransactionalOperator
import java.time.Instant

internal class CatalogServiceImpl(
    private val database: CatalogDatabase,
    private val transactionalOperator: TransactionalOperator,
    private val kafkaTemplate: KafkaTemplate<String, ByteArray>,
    private val topicNameCatalogBookInstanceAdded: String,
) : CatalogService {
    override suspend fun addBook(author: String, title: String, isbn: String): Either<CatalogError, Book> {
        return either {
            val book = Book(
                isbn = ISBN.from(isbn).mapLeft { CatalogError.Input(it.message) }.bind(),
                title = BookTitle.from(title).mapLeft { CatalogError.Input(it.message) }.bind(),
                author = BookAuthor.from(author).mapLeft { CatalogError.Input(it.message) }.bind(),
            )
            database.saveNew(book).mapLeft { CatalogError.System(it) }.bind()
            book
        }
    }

    override suspend fun addBookInstance(isbnLiteral: String, bookType: BookType): Either<CatalogError, BookInstance> {
        return transactionalEither(transactionalOperator) {
            val isbn = ISBN.from(isbnLiteral).mapLeft { CatalogError.Input(it.message) }.bind()
            val bookInstance = database.findBy(isbn).mapLeft { CatalogError.System(it) }.bind()
                .map { BookInstance(isbn, BookId.uniqueOne(), bookType) }
                .getOrElse { raise(CatalogError.BookNotExist(isbn)) }
            saveAndPublishEvent(bookInstance).bind()
        }
    }

    override suspend fun findAllBooks(): Either<CatalogError.System, List<Book>> {
        return database.findAllBooks().mapLeft { CatalogError.System(it) }
    }

    override suspend fun findAllBookInstances(): Either<CatalogError.System, List<BookInstance>> {
        return database.findAllBookInstances().mapLeft { CatalogError.System(it) }
    }

    private suspend fun saveAndPublishEvent(bookInstance: BookInstance): Either<CatalogError, BookInstance> {
        return either {
            database.saveNew(bookInstance).mapLeft { CatalogError.System(it) }.bind()
            val event = BookInstanceAddedToCatalogue.newBuilder()
                .setEventId(DomainEventId.uniqueOne().source)
                .setIsbn(bookInstance.bookIsbn.source)
                .setBookId(bookInstance.bookId.source)
                .setBookType(
                    when (bookInstance.bookType) {
                        BookType.Restricted -> BOOK_TYPE_RESTRICTED
                        BookType.Circulating -> BOOK_TYPE_CIRCULATING
                    },
                )
                .setInstant(Instant.now().epochSecond)
                .build()
            Either.catch { kafkaTemplate.send(topicNameCatalogBookInstanceAdded, event.toByteArray()).await() }
                .mapLeft { CatalogError.System(SystemError.IOError("Kafka 메시지 전송에 실패했습니다: ${it.message}")) }
                .bind()
            bookInstance
        }
    }
}
