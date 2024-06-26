package io.chrislee.library.lending.dailysheet.application.domain

import io.chrislee.library.common.domain.NewType
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.OverdueCheckoutRegisteredEvent
import io.chrislee.library.lending.patron.application.domain.PatronId

internal data class OverdueCheckout(val bookId: BookId, val patronId: PatronId)

internal class CheckoutsToOverdueSheet(
    override val source: List<OverdueCheckout>,
) : NewType<List<OverdueCheckout>> {
    fun toEvents(): List<OverdueCheckoutRegisteredEvent> {
        return source.map {
            OverdueCheckoutRegisteredEvent(
                patronId = it.patronId,
                bookId = it.bookId,
            )
        }
    }

    fun count(): Int {
        return source.size
    }
}
