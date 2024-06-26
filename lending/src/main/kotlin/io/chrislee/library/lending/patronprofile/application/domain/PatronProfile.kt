package io.chrislee.library.lending.patronprofile.application.domain

import arrow.core.Option
import arrow.core.toOption
import io.chrislee.library.common.domain.NewType
import io.chrislee.library.lending.book.application.domain.BookId
import java.time.Instant

internal data class Hold(val bookId: BookId, val till: Option<Instant>)

internal data class Checkout(val bookId: BookId, val till: Instant)

@JvmInline
internal value class HoldsView(override val source: Map<BookId, Hold>) : NewType<Map<BookId, Hold>> {
    fun asSequence(): Sequence<Hold> {
        return source.values.asSequence()
    }

    fun findHold(bookId: BookId): Option<Hold> {
        return source[bookId].toOption()
    }
}

@JvmInline
internal value class CheckoutsView(override val source: Map<BookId, Checkout>) : NewType<Map<BookId, Checkout>> {
    fun findCheckout(bookId: BookId): Option<Checkout> {
        return source[bookId].toOption()
    }
}

internal class PatronProfile(val holdsView: HoldsView, val checkoutsView: CheckoutsView) {
    fun findHold(bookId: BookId): Option<Hold> {
        return holdsView.findHold(bookId)
    }

    fun findCheckout(bookId: BookId): Option<Checkout> {
        return checkoutsView.findCheckout(bookId)
    }
}
