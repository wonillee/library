package io.chrislee.library.lending.book.adapter.persistence

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.some
import arrow.core.toOption
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.common.infrastructure.transformToSystemError
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.Book
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.book.application.domain.BookRepository
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.chrislee.library.lending.patron.application.usecase.FindAvailableBookQuery
import io.chrislee.library.lending.patron.application.usecase.FindBookOnHoldQuery

// TODO Query interface들은 모두 patron 도메인에서 유의미하다.
//  book과 patron의 경계가 서비스 단위로 분리되면 patron 도메인에서 별도로 book에 대한 상태 정보를 동기화해야 한다.
internal class DatabaseBookRepository(
    private val internalRepository: R2DBCBookRepository,
) : BookRepository, FindAvailableBookQuery, FindBookOnHoldQuery {
    override suspend fun findByBookId(bookId: BookId): Either<SystemError, Option<Book>> =
        either {
            Either.catch { internalRepository.findByBookId(bookId).toOption() }
                .mapLeft { transformToSystemError(it) }.bind()
                .map { it.toDomain().bind() }
        }

    override suspend fun save(book: Book): Either<SystemError, Unit> =
        either {
            val entity = Either.catch { internalRepository.findByBookId(book.bookId).toOption() }
                .mapLeft { transformToSystemError(it) }.bind()
                .map { BookEntity.fromDomain(book) }
                .getOrElse { BookEntity.fromDomain(book, forCreation = true) }
            Either.catch { internalRepository.save(entity) }
                .mapLeft { transformToSystemError(it) }.bind()
        }

    override suspend fun findAvailableBookByBookId(bookId: BookId): Either<SystemError, Option<AvailableBook>> {
        return either {
            Either.catch { findByBookId(bookId).bind() }
                .mapLeft { transformToSystemError(it) }.bind()
                .flatMap { book ->
                    when (book) {
                        is AvailableBook -> book.some()
                        else -> None
                    }
                }
        }
    }

    override suspend fun findBookOnHold(bookId: BookId, patonId: PatronId): Either<SystemError, Option<BookOnHold>> {
        return either {
            Either.catch { findByBookId(bookId).bind() }
                .mapLeft { transformToSystemError(it) }.bind()
                .flatMap { book ->
                    when (book) {
                        is BookOnHold -> book.some()
                        else -> None
                    }
                }
        }
    }
}
