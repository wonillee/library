package io.chrislee.library.catalog.adapter.persistence

import arrow.core.Either
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.toOption
import io.chrislee.library.catalog.application.domain.Book
import io.chrislee.library.catalog.application.domain.BookAuthor
import io.chrislee.library.catalog.application.domain.BookId
import io.chrislee.library.catalog.application.domain.BookInstance
import io.chrislee.library.catalog.application.domain.BookTitle
import io.chrislee.library.catalog.application.domain.BookType
import io.chrislee.library.catalog.application.domain.ISBN
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.common.infrastructure.R2DBCColumnToDomainConverter.convert
import io.chrislee.library.common.infrastructure.transformToSystemError
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.dao.DuplicateKeyException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.r2dbc.core.awaitRowsUpdated

internal class CatalogDatabase(private val databaseClient: DatabaseClient) {
    suspend fun saveNew(book: Book): Either<SystemError, Book> {
        return either {
            Either.catch {
                databaseClient.sql(
                    """
                    INSERT INTO book (
                        isbn,
                        title,
                        author
                    ) VALUES (
                        :isbn,
                        :title,
                        :author
                    )
                    """.trimIndent(),
                )
                    .bind("isbn", book.isbn.source)
                    .bind("title", book.title.source)
                    .bind("author", book.author.source)
                    .fetch().awaitRowsUpdated()
            }
                .mapLeft {
                    if (it is DuplicateKeyException) {
                        SystemError.DataInconsistency("ISBN이 중복됩니다: ${book.isbn.source}")
                    } else {
                        transformToSystemError(it)
                    }
                }
                .bind()
            book
        }
    }

    suspend fun saveNew(bookInstance: BookInstance): Either<SystemError, BookInstance> {
        return either {
            Either.catch {
                databaseClient.sql(
                    """
                        INSERT INTO book_instance (
                            isbn,
                            book_id,
                            book_type
                        ) VALUES (
                            :isbn,
                            :bookId,
                            :bookType
                        )
                    """.trimIndent(),
                )
                    .bind("isbn", bookInstance.bookIsbn.source)
                    .bind("bookId", bookInstance.bookId.source)
                    .bind("bookType", bookInstance.bookType.name)
                    .fetch().awaitRowsUpdated()
            }
                .mapLeft { transformToSystemError(it) }
                .bind()
            bookInstance
        }
    }

    suspend fun findBy(isbn: ISBN): Either<SystemError, Option<Book>> {
        return either {
            Either.catch {
                databaseClient
                    .sql("SELECT * FROM book WHERE isbn = :isbn")
                    .bind("isbn", isbn.source)
                    .fetch().awaitOneOrNull().toOption()
            }
                .mapLeft { transformToSystemError(it) }.bind()
                .map { eitherBookOrError(it).bind() }
        }
    }

    suspend fun findAllBooks(): Either<SystemError, List<Book>> {
        return either {
            Either.catch {
                databaseClient
                    .sql("SELECT * FROM book").fetch().all()
                    .reduce(emptyList<Book?>()) { books, row -> books + eitherBookOrError(row).getOrNull() }
                    .awaitSingleOrNull()
                    .toOption()
                    .map { it.filterNotNull() }
                    .getOrElse { emptyList() }
            }
                .mapLeft { transformToSystemError(it) }.bind()
        }
    }

    suspend fun findAllBookInstances(): Either<SystemError, List<BookInstance>> {
        return either {
            Either.catch {
                databaseClient
                    .sql("SELECT * FROM book_instance").fetch().all()
                    .reduce(emptyList<BookInstance?>()) { instances, row ->
                        instances + eitherBookInstanceOrError(row)
                            .onLeft { logger.error { "도서 인스턴스 매핑 오류: ${it.message}" } }
                            .getOrNull()
                    }
                    .awaitSingleOrNull()
                    .toOption()
                    .map { it.filterNotNull() }
                    .getOrElse { emptyList() }
            }
                .mapLeft { transformToSystemError(it) }.bind()
        }
    }

    private fun dataInconsistency(columnName: String, row: Map<String, Any>): SystemError.DataInconsistency {
        return SystemError.DataInconsistency("데이터 일관성에 위배됩니다: 컬럼 $columnName 데이터 ${row[columnName]}")
    }

    private fun eitherBookOrError(row: Map<String, Any>): Either<SystemError, Book> {
        return either {
            Book(
                isbn = row.convert("isbn", ISBN::from) { dataInconsistency("isbn", row) }.bind(),
                title = row.convert("title", BookTitle::from) { dataInconsistency("title", row) }.bind(),
                author = row.convert("author", BookAuthor::from) { dataInconsistency("author", row) }.bind(),
            )
        }
    }

    private fun eitherBookInstanceOrError(row: Map<String, Any>): Either<SystemError, BookInstance> {
        return either {
            BookInstance(
                bookIsbn = row.convert("isbn", ISBN::from) { dataInconsistency("isbn", row) }.bind(),
                bookId = row.convert("book_id", BookId::from) { dataInconsistency("book_id", row) }.bind(),
                bookType = Either.catch { (BookType.valueOf(row["book_type"] as String)) }
                    .mapLeft { dataInconsistency("book_type", row) }.bind(),
            )
        }
    }

    companion object {
        private val logger = mu.KotlinLogging.logger { }
    }
}
