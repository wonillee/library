package io.chrislee.library.lending.patron.application.usecase

import arrow.core.Either
import arrow.core.Option
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.BookId

@FunctionalInterface
internal interface FindAvailableBookQuery {
    suspend fun findAvailableBookByBookId(bookId: BookId): Either<SystemError, Option<AvailableBook>>
}
