package io.chrislee.library.lending.dailysheet.application.domain

import arrow.core.Either
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent

internal interface DailySheet {
    suspend fun queryForCheckoutsToOverdue(): Either<SystemError, CheckoutsToOverdueSheet>

    suspend fun queryForHoldsToExpireSheet(): Either<SystemError, HoldsToExpireSheet>

    suspend fun handle(event: BookPlacedOnHoldEvent)

    suspend fun handle(event: BookHoldCancelledEvent)

    suspend fun handle(event: BookHoldExpiredEvent)

    suspend fun handle(event: BookCheckedOutEvent)

    suspend fun handle(event: BookReturnedEvent)
}
