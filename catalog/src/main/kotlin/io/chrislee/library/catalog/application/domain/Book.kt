package io.chrislee.library.catalog.application.domain

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.chrislee.library.common.domain.InvalidStateError
import io.chrislee.library.common.domain.NewType

internal data class Book(
    val isbn: ISBN,
    val title: BookTitle,
    val author: BookAuthor,
)

@JvmInline
internal value class ISBN private constructor(override val source: String) : NewType<String> {
    companion object {
        fun from(isbn: String): Either<InvalidStateError, ISBN> {
            return either {
                ensure(isbn.trim().isNotBlank()) { InvalidStateError("잘못된 ISBN 형식입니다") }
                ISBN(isbn.trim())
            }
        }
    }
}

@JvmInline
internal value class BookTitle private constructor(override val source: String) : NewType<String> {
    companion object {
        fun from(title: String): Either<InvalidStateError, BookTitle> {
            return either {
                ensure(title.isNotBlank()) { InvalidStateError("도서명이 비어 있습니다") }
                BookTitle(title.trim())
            }
        }
    }
}

@JvmInline
internal value class BookAuthor private constructor(override val source: String) : NewType<String> {
    companion object {
        fun from(author: String): Either<InvalidStateError, BookAuthor> {
            return either {
                ensure(author.isNotBlank()) { InvalidStateError("저자명이 비어 있습니다") }
                BookAuthor(author.trim())
            }
        }
    }
}
