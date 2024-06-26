package io.chrislee.library.lending.book.adapter.persistence

import arrow.core.some
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.book.application.domain.CheckedOutBook
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.time.Instant

class BookTableMappingTest : DescribeSpec({
    val patronId = PatronId.uniqueOne()
    val bookId = BookId.uniqueOne()
    val holdTill = Instant.now()

    fun bookEntity(
        bookState: BookState,
        patronId: PatronId? = null,
        holdTill: Instant? = null,
    ): BookEntity {
        return BookEntity(
            bookId = bookId,
            bookType = BookType.Circulating,
            bookState = bookState,
            byPatron = patronId,
            onHoldTill = holdTill,
        )
    }

    it("대여 가능한 도서로 매핑 가능해야 한다") {
        val entity = bookEntity(BookState.Available)
        val model = entity.toDomain().shouldBeRight()
        expectThat(model).isA<AvailableBook>().and {
            get { this.bookId }.isEqualTo(entity.bookId)
            get { this.bookType }.isEqualTo(entity.bookType)
        }
    }

    it("대여 예약된 도서로 매핑 가능해야 한다") {
        val entity = bookEntity(BookState.OnHold, patronId, holdTill)
        val model = entity.toDomain().shouldBeRight()
        expectThat(model).isA<BookOnHold>().and {
            get { this.bookId }.isEqualTo(entity.bookId)
            get { this.bookType }.isEqualTo(entity.bookType)
            get { this.byPatron }.isEqualTo(entity.byPatron)
            get { this.holdTill }.isEqualTo(holdTill.some())
        }
    }

    it("대여된 도서로 매핑 가능해야 한다") {
        val entity = bookEntity(BookState.CheckedOut, patronId)
        val model = entity.toDomain().shouldBeRight()
        expectThat(model).isA<CheckedOutBook>().and {
            get { this.bookId }.isEqualTo(entity.bookId)
            get { this.bookType }.isEqualTo(entity.bookType)
            get { this.byPatron }.isEqualTo(entity.byPatron)
        }
    }
})
