package io.chrislee.library.lending.patron.application.usecase

import arrow.core.Either
import arrow.core.Option
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.patron.application.domain.PatronId

@FunctionalInterface
internal interface FindBookOnHoldQuery {
    suspend fun findBookOnHold(bookId: BookId, patonId: PatronId): Either<SystemError, Option<BookOnHold>>
}
