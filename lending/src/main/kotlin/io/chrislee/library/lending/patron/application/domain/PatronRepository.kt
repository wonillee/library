package io.chrislee.library.lending.patron.application.domain

import arrow.core.Either
import arrow.core.Option
import io.chrislee.library.common.domain.SystemError

internal interface PatronRepository {
    suspend fun findByPatronId(patronId: PatronId): Either<SystemError, Option<Patron>>

    suspend fun save(event: PatronCreatedEvent): Either<SystemError, Patron>

    suspend fun save(currentPatron: Patron, event: PatronEvent): Either<SystemError, Patron>
}
