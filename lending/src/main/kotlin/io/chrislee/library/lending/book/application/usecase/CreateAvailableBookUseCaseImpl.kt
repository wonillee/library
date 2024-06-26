package io.chrislee.library.lending.book.application.usecase

import arrow.core.Either
import arrow.core.raise.either
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.Book
import io.chrislee.library.lending.book.application.domain.BookRepository

internal class CreateAvailableBookUseCaseImpl(
    private val bookRepository: BookRepository,
) : CreateAvailableBookUseCase {
    override suspend fun execute(command: CreateAvailableBookCommand): Either<SystemError, Book> {
        return either {
            val bookToBeCreated = AvailableBook(command.bookId, command.bookType)
            bookRepository.findByBookId(command.bookId).bind().fold(
                ifEmpty = { bookRepository.save(bookToBeCreated).bind() },
                ifSome = { raise(SystemError.DataInconsistency("동일한 도서 ID가 존재합니다: $command")) },
            )
            bookToBeCreated
        }
    }
}
