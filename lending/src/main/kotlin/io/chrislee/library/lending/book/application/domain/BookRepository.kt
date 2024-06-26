package io.chrislee.library.lending.book.application.domain

import arrow.core.Either
import arrow.core.Option
import io.chrislee.library.common.domain.SystemError

internal interface BookRepository {
    suspend fun findByBookId(bookId: BookId): Either<SystemError, Option<Book>>

    suspend fun save(book: Book): Either<SystemError, Unit>
}
