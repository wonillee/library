package io.chrislee.library.lending.dailysheet.adapter.persistence

import arrow.core.Either
import arrow.core.None
import arrow.core.Some
import arrow.core.raise.either
import io.chrislee.library.common.domain.DomainEvent
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.common.infrastructure.R2DBCColumnToDomainConverter.convert
import io.chrislee.library.common.infrastructure.transformToSystemError
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.dailysheet.application.domain.CheckoutsToOverdueSheet
import io.chrislee.library.lending.dailysheet.application.domain.DailySheet
import io.chrislee.library.lending.dailysheet.application.domain.ExpiredHold
import io.chrislee.library.lending.dailysheet.application.domain.HoldsToExpireSheet
import io.chrislee.library.lending.dailysheet.application.domain.OverdueCheckout
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent
import io.chrislee.library.lending.patron.application.domain.PatronId
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.context.event.EventListener
import org.springframework.dao.DuplicateKeyException
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.Instant
import java.time.OffsetDateTime

internal open class DatabaseDailySheetReadModel(
    private val databaseClient: DatabaseClient,
    private val clock: Clock,
) : DailySheet {
    override suspend fun queryForHoldsToExpireSheet(): Either<SystemError, HoldsToExpireSheet> {
        return either {
            Either.catch {
                databaseClient.sql(
                    """
                SELECT
                    h.book_id,
                    h.hold_by_patron_id
                FROM holds_sheet h
                WHERE h.status = 'ACTIVE' and h.hold_till <= :holdTill
                    """.trimIndent(),
                )
                    .bind("holdTill", OffsetDateTime.now(clock)).fetch().all()
                    .map { row ->
                        either {
                            ExpiredHold(
                                patronId = row.convert("hold_by_patron_id", PatronId::from) {
                                    SystemError.DataInconsistency("고객 ID 변환에 실패했습니다: $it")
                                }.bind(),
                                bookId = row.convert("book_id", BookId::from) {
                                    SystemError.DataInconsistency("도서 ID 변환에 실패했습니다: $it")
                                }.bind(),
                            )
                        }.getOrNull()
                    }
                    .reduce(emptyList<ExpiredHold>()) { list, expired ->
                        if (expired == null) list else list + expired
                    }
                    .awaitSingle()
                    .let { HoldsToExpireSheet(it) }
            }
                .mapLeft { transformToSystemError(it) }
                .bind()
        }
    }

    override suspend fun queryForCheckoutsToOverdue(): Either<SystemError, CheckoutsToOverdueSheet> {
        return Either.catch {
            databaseClient.sql(
                """
                    SELECT
                        c.book_id,
                        c.checked_out_by_patron_id
                    FROM checkouts_sheet c
                    WHERE c.status = 'CHECKEDOUT' and c.checkout_till <= :checkoutTill
                """.trimIndent(),
            )
                .bind("checkoutTill", OffsetDateTime.now(clock)).fetch().all()
                .map { row ->
                    either {
                        OverdueCheckout(
                            patronId = row.convert("checked_out_by_patron_id", PatronId::from) {
                                SystemError.DataInconsistency("고객 ID 변환에 실패했습니다: $it")
                            }.bind(),
                            bookId = row.convert("book_id", BookId::from) {
                                SystemError.DataInconsistency("도서 ID 변환에 실패했습니다: $it")
                            }.bind(),
                        )
                    }.getOrNull()
                }
                .reduce(emptyList<OverdueCheckout>()) { list, overdue ->
                    if (overdue == null) list else list + overdue
                }
                .awaitSingle()
                .let { CheckoutsToOverdueSheet(it) }
        }
            .mapLeft { transformToSystemError(it) }
    }

    @Transactional
    @EventListener
    override suspend fun handle(event: BookPlacedOnHoldEvent) {
        try {
            databaseClient.sql(
                """
                INSERT INTO holds_sheet (
                    book_id,
                    status,
                    hold_event_id,
                    hold_by_patron_id,
                    hold_at,
                    hold_till,
                    expired_at,
                    cancelled_at,
                    checked_out_at
                ) VALUES (
                    :bookId,
                    'ACTIVE',
                    :eventId,
                    :patronId,
                    :holdAt,
                    :holdTill,
                    null,
                    null,
                    null
                )
            """,
            )
                .bind("bookId", event.bookId.source)
                .bind("eventId", event.id.source)
                .bind("patronId", event.patronId.source)
                .bind("holdAt", OffsetDateTime.ofInstant(event.occurredAt, clock.zone))
                .let {
                    when (val holdTill = event.holdDuration.holdTill()) {
                        None -> it.bindNull("holdTill", OffsetDateTime::class.java)
                        is Some -> it.bind("holdTill", OffsetDateTime.ofInstant(holdTill.value, clock.zone))
                    }
                }
                .fetch().awaitRowsUpdated()
        } catch (e: DuplicateKeyException) {
            // ignore duplicate intentionally
        }
    }

    @Transactional
    @EventListener
    override suspend fun handle(event: BookHoldCancelledEvent) {
        databaseClient.sql(
            """
                UPDATE holds_sheet
                SET cancelled_at = :cancelledAt, status = 'CANCELLED'
                WHERE cancelled_at IS NULL AND book_id = :bookId AND hold_by_patron_id = :patronId
            """.trimIndent(),
        )
            .bind("cancelledAt", OffsetDateTime.ofInstant(event.occurredAt, clock.zone))
            .bind("bookId", event.bookId.source)
            .bind("patronId", event.patronId.source)
            .fetch().awaitRowsUpdated()
    }

    @Transactional
    @EventListener
    override suspend fun handle(event: BookHoldExpiredEvent) {
        databaseClient.sql(
            """
                UPDATE holds_sheet
                SET expired_at = :expiredAt, status = 'EXPIRED'
                WHERE expired_at IS NULL AND book_id = :bookId AND hold_by_patron_id = :patronId
            """.trimIndent(),
        )
            .bind("expiredAt", OffsetDateTime.ofInstant(event.occurredAt, clock.zone))
            .bind("bookId", event.bookId.source)
            .bind("patronId", event.patronId.source)
            .fetch().awaitRowsUpdated()
    }

    @Transactional
    @EventListener
    override suspend fun handle(event: BookCheckedOutEvent) {
        try {
            createCheckoutsSheetRow(
                bookId = event.bookId,
                patronId = event.patronId,
                status = "CHECKEDOUT",
                checkoutedAt = event.occurredAt,
                checkoutTill = event.till,
                returnedAt = null,
                event = event,
            )
        } catch (e: DuplicateKeyException) {
            // ignore duplicate intentionally
        }
        databaseClient.sql(
            """
                UPDATE holds_sheet
                SET checked_out_at = :checkedOutAt, status = 'CHECKEDOUT'
                WHERE checked_out_at IS NULL AND book_id = :bookId AND hold_by_patron_id = :patronId
            """.trimIndent(),
        )
            .bind("checkedOutAt", OffsetDateTime.ofInstant(event.occurredAt, clock.zone))
            .bind("bookId", event.bookId.source)
            .bind("patronId", event.patronId.source)
            .fetch().awaitRowsUpdated()
    }

    @Transactional
    @EventListener
    override suspend fun handle(event: BookReturnedEvent) {
        // mark as RETURNED
        val count = databaseClient.sql(
            """
                UPDATE checkouts_sheet
                SET returned_at = :returnedAt, status = 'RETURNED'
                WHERE returned_at IS NULL AND book_id = :bookId AND checked_out_by_patron_id = :patronId
            """.trimIndent(),
        )
            .bind("returnedAt", OffsetDateTime.ofInstant(event.occurredAt, clock.zone))
            .bind("bookId", event.bookId.source)
            .bind("patronId", event.patronId.source)
            .fetch().awaitRowsUpdated()
        // if row doesn't exist, we will create one
        if (count == 0L) {
            createCheckoutsSheetRow(
                bookId = event.bookId,
                patronId = event.patronId,
                status = "RETURNED",
                checkoutedAt = null,
                checkoutTill = null,
                returnedAt = event.occurredAt,
                event = event,
            )
        }
    }

    private suspend fun createCheckoutsSheetRow(
        bookId: BookId,
        patronId: PatronId,
        status: String,
        checkoutedAt: Instant?,
        checkoutTill: Instant?,
        returnedAt: Instant?,
        event: DomainEvent,
    ) {
        databaseClient.sql(
            """
                INSERT INTO checkouts_sheet (
                    book_id,
                    status,
                    checkout_event_id,
                    checked_out_by_patron_id,
                    checked_out_at,
                    checkout_till,
                    returned_at
                ) VALUES (
                    :bookId,
                    :status,
                    :eventId,
                    :patronId,
                    :checkedOutAt,
                    :checkoutTill,
                    :returnedAt
                )
            """.trimIndent(),
        )
            .bind("bookId", bookId.source)
            .bind("eventId", event.id.source)
            .bind("patronId", patronId.source)
            .bind("status", status)
            .let {
                if (checkoutedAt == null) {
                    it.bindNull("checkedOutAt", OffsetDateTime::class.java)
                } else {
                    it.bind("checkedOutAt", OffsetDateTime.ofInstant(checkoutedAt, clock.zone))
                }
            }
            .let {
                if (checkoutTill == null) {
                    it.bindNull("checkoutTill", OffsetDateTime::class.java)
                } else {
                    it.bind("checkoutTill", OffsetDateTime.ofInstant(checkoutTill, clock.zone))
                }
            }
            .let {
                if (returnedAt == null) {
                    it.bindNull("returnedAt", OffsetDateTime::class.java)
                } else {
                    it.bind("returnedAt", OffsetDateTime.ofInstant(returnedAt, clock.zone))
                }
            }
            .fetch().awaitRowsUpdated()
    }
}
