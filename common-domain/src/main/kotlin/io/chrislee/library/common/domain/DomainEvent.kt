package io.chrislee.library.common.domain

import java.time.Instant
import java.util.UUID

@JvmInline
value class DomainEventId private constructor(override val source: String) : NewType<String> {
    companion object {
        fun uniqueOne(): DomainEventId = DomainEventId(UUID.randomUUID().toString())
    }
}

interface DomainEvent {
    val id: DomainEventId
    val occurredAt: Instant
}
