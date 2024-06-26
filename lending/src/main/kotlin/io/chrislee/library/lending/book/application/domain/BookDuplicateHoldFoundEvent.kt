package io.chrislee.library.lending.book.application.domain

import io.chrislee.library.common.domain.DomainEvent
import io.chrislee.library.common.domain.DomainEventId
import io.chrislee.library.lending.patron.application.domain.PatronId
import java.time.Instant

internal data class BookDuplicateHoldFoundEvent(
    override val id: DomainEventId = DomainEventId.uniqueOne(),
    override val occurredAt: Instant = Instant.now(),
    val bookId: BookId,
    val firstPatronId: PatronId,
    val secondPatronId: PatronId,
) : DomainEvent
