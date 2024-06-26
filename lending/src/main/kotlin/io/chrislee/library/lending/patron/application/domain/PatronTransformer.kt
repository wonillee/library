package io.chrislee.library.lending.patron.application.domain

import io.chrislee.library.lending.patron.application.domain.Hold.Companion.asHold

internal object PatronTransformer {
    fun transform(patron: Patron, event: BookPlacedOnHoldEvent): Patron {
        return patron.copy(patronHolds = patron.patronHolds.add(event.bookId.asHold()))
    }

    fun transform(patron: Patron, event: BookCheckedOutEvent): Patron {
        return patron.copy(patronHolds = patron.patronHolds.remove(event.bookId.asHold()))
    }

    fun transform(patron: Patron, event: BookHoldCancelledEvent): Patron {
        return patron.copy(patronHolds = patron.patronHolds.remove(event.bookId.asHold()))
    }

    fun transform(patron: Patron, event: BookHoldExpiredEvent): Patron {
        return patron.copy(patronHolds = patron.patronHolds.remove(event.bookId.asHold()))
    }

    fun transform(patron: Patron, event: OverdueCheckoutRegisteredEvent): Patron {
        return patron.copy(overdueCheckouts = patron.overdueCheckouts.add(event.bookId))
    }

    fun transform(patron: Patron, event: BookReturnedEvent): Patron {
        return patron.copy(overdueCheckouts = patron.overdueCheckouts.remove(event.bookId))
    }
}
