package io.chrislee.library.lending.dailysheet.application.domain

import io.chrislee.library.common.domain.NewType
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.PatronId

internal data class ExpiredHold(
    val patronId: PatronId,
    val bookId: BookId,
)

@JvmInline
internal value class HoldsToExpireSheet(override val source: List<ExpiredHold>) : NewType<List<ExpiredHold>> {
    fun toEvents(): List<BookHoldExpiredEvent> {
        return source.map {
            BookHoldExpiredEvent(
                patronId = it.patronId,
                bookId = it.bookId,
            )
        }
    }

    fun count(): Int = source.count()
}
