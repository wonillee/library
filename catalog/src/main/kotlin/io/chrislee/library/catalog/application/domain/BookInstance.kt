package io.chrislee.library.catalog.application.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.chrislee.library.common.domain.InvalidStateError
import io.chrislee.library.common.domain.NewType
import java.util.UUID

internal data class BookInstance(
    val bookIsbn: ISBN,
    val bookId: BookId,
    val bookType: BookType,
)

@JvmInline
internal value class BookId private constructor(override val source: String) : NewType<String> {
    companion object {
        fun from(bookId: String): Either<InvalidStateError, BookId> {
            return either {
                ensure(bookId.isNotBlank()) { InvalidStateError(("도서 ID가 비어 있습니다")) }
                BookId(bookId.trim())
            }
        }

        fun uniqueOne(): BookId = BookId(UUID.randomUUID().toString())
    }
}

internal enum class BookType {
    Restricted,
    Circulating,
}
