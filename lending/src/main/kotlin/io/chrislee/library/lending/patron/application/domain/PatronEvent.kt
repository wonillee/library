package io.chrislee.library.lending.patron.application.domain

import arrow.core.Option
import io.chrislee.library.common.domain.DomainEvent
import io.chrislee.library.common.domain.DomainEventId
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookType
import java.time.Instant

internal sealed interface PatronEvent : DomainEvent {
    val patronId: PatronId
}

internal data class PatronCreatedEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val patronType: Patron.Type,
) : PatronEvent

internal data class BookPlacedOnHoldEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookId: BookId,
    val bookType: BookType,
    val holdDuration: HoldDuration,
) : PatronEvent

internal data class BookPlacedOnHoldEvents(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookPlacedOnHoldEvent: BookPlacedOnHoldEvent,
    val maximumNumberOnHoldsReachedEvent: Option<MaximumNumberOfHoldsReachedEvent>,
) : PatronEvent

internal data class MaximumNumberOfHoldsReachedEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
) : PatronEvent

internal data class BookHoldFailedEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookId: BookId,
    val reason: String,
) : PatronEvent

internal data class BookHoldCancelledEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookId: BookId,
) : PatronEvent

internal data class BookHoldCancellingFailedEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookId: BookId,
    val reason: Reason,
    val details: String,
) : PatronEvent {
    enum class Reason {
        BookNotFound,
        PatronNotFound,
        BookNotHeld,
        System,
    }
}

internal data class BookCheckingOutFailedEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookId: BookId,
    val reason: String,
) : PatronEvent

internal data class BookCheckedOutEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookId: BookId,
    val bookType: BookType,
    val till: Instant,
) : PatronEvent

internal data class BookReturnedEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookId: BookId,
    val bookType: BookType,
) : PatronEvent

internal data class BookHoldExpiredEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookId: BookId,
) : PatronEvent

internal data class OverdueCheckoutRegisteredEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    override val patronId: PatronId,
    val bookId: BookId,
) : PatronEvent
