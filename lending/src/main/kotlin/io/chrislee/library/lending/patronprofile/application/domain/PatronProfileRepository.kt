package io.chrislee.library.lending.patronprofile.application.domain

import arrow.core.Either
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.patron.application.domain.PatronId

internal interface PatronProfileRepository {
    suspend fun findByPatronId(patronId: PatronId): Either<SystemError, PatronProfile>
}
