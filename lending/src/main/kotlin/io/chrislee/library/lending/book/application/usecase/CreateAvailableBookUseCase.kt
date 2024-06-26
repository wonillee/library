package io.chrislee.library.lending.book.application.usecase

import arrow.core.Either
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.Book
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookType

internal interface CreateAvailableBookUseCase {
    suspend fun execute(command: CreateAvailableBookCommand): Either<SystemError, Book>
}

internal data class CreateAvailableBookCommand(val bookId: BookId, val bookType: BookType)
