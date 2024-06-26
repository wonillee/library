package io.chrislee.library.catalog.application.domain

import arrow.core.Either
import io.chrislee.library.common.domain.SystemError

internal interface CatalogService {
    suspend fun addBook(author: String, title: String, isbn: String): Either<CatalogError, Book>

    suspend fun addBookInstance(isbnLiteral: String, bookType: BookType): Either<CatalogError, BookInstance>

    suspend fun findAllBooks(): Either<CatalogError.System, List<Book>>

    suspend fun findAllBookInstances(): Either<CatalogError.System, List<BookInstance>>
}

internal sealed class CatalogError {
    data class Input(val message: String) : CatalogError()

    data class BookNotExist(val isbn: ISBN) : CatalogError()

    data class System(val systemError: SystemError) : CatalogError()
}
