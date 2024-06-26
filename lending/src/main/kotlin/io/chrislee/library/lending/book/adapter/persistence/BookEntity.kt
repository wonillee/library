package io.chrislee.library.lending.book.adapter.persistence

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.toOption
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.Book
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.book.application.domain.CheckedOutBook
import io.chrislee.library.lending.patron.application.domain.PatronId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.annotation.Transient
import org.springframework.data.domain.Persistable
import org.springframework.data.relational.core.mapping.Column
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

internal enum class BookState {
    Available,
    OnHold,
    CheckedOut,
}

@Table("book")
internal data class BookEntity(
    @Id val bookId: BookId,
    @Column val bookType: BookType,
    @Column val bookState: BookState,
    @Column val byPatron: PatronId?,
    @Column val onHoldTill: Instant?,
    @Transient private val forCreation: Boolean = false,
) : Persistable<String> {
    @PersistenceCreator
    constructor(
        bookId: BookId,
        bookType: BookType,
        bookState: BookState,
        byPatron: PatronId?,
        onHoldTill: Instant?,
    ) : this(bookId, bookType, bookState, byPatron, onHoldTill, false)

    fun toDomain(): Either<SystemError.DataInconsistency, Book> =
        either {
            ensure(isBookStateValid()) { SystemError.DataInconsistency("도서 엔티티 상태 이상: ${this@BookEntity}") }
            when (bookState) {
                BookState.Available -> AvailableBook(bookId, bookType)
                BookState.OnHold -> BookOnHold(bookId, bookType, byPatron!!, onHoldTill.toOption())
                BookState.CheckedOut -> CheckedOutBook(bookId, bookType, byPatron!!)
            }
        }

    private fun isBookStateValid(): Boolean {
        return when (bookState) {
            BookState.Available -> byPatron == null && onHoldTill == null
            BookState.OnHold -> byPatron != null
            BookState.CheckedOut -> byPatron != null && onHoldTill == null
        }
    }

    companion object {
        fun fromDomain(
            book: Book,
            forCreation: Boolean = false,
        ): BookEntity {
            return BookEntity(
                bookId = book.bookId,
                bookType = book.bookType,
                bookState = when (book) {
                    is AvailableBook -> BookState.Available
                    is CheckedOutBook -> BookState.CheckedOut
                    is BookOnHold -> BookState.OnHold
                },
                byPatron = when (book) {
                    is AvailableBook -> null
                    is CheckedOutBook -> book.byPatron
                    is BookOnHold -> book.byPatron
                },
                onHoldTill = when (book) {
                    is BookOnHold -> book.holdTill.getOrNull()
                    else -> null
                },
                forCreation = forCreation,
            )
        }
    }

    override fun getId(): String {
        return bookId.source
    }

    override fun isNew(): Boolean {
        return forCreation
    }
}
