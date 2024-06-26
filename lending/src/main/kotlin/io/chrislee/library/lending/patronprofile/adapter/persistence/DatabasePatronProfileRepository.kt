package io.chrislee.library.lending.patronprofile.adapter.persistence

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.toOption
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.common.infrastructure.R2DBCColumnToDomainConverter.convert
import io.chrislee.library.common.infrastructure.transformToSystemError
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.chrislee.library.lending.patronprofile.application.domain.Checkout
import io.chrislee.library.lending.patronprofile.application.domain.CheckoutsView
import io.chrislee.library.lending.patronprofile.application.domain.Hold
import io.chrislee.library.lending.patronprofile.application.domain.HoldsView
import io.chrislee.library.lending.patronprofile.application.domain.PatronProfile
import io.chrislee.library.lending.patronprofile.application.domain.PatronProfileRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import java.time.OffsetDateTime

internal class DatabasePatronProfileRepository(private val databaseClient: DatabaseClient) : PatronProfileRepository {
    override suspend fun findByPatronId(patronId: PatronId): Either<SystemError, PatronProfile> {
        return either {
            PatronProfile(
                holdsView = findCurrentHoldsFor(patronId).bind(),
                checkoutsView = findCurrentCheckoutsFor(patronId).bind(),
            )
        }
    }

    private suspend fun findCurrentHoldsFor(patronId: PatronId): Either<SystemError, HoldsView> {
        return either {
            Either.catch {
                databaseClient.sql(
                    """
                        SELECT
                            h.book_id,
                            h.hold_till
                        FROM holds_sheet h
                        WHERE
                            h.hold_by_patron_id = :patronId
                        AND h.checked_out_at IS NULL
                        AND h.expired_at IS NULL
                        AND h.cancelled_at IS NULL
                    """.trimIndent(),
                )
                    .bind("patronId", patronId.source).fetch().all()
                    .map { it.toHold().getOrNull() }
                    .reduce(HoldsView(emptyMap())) { acc, hold ->
                        if (hold == null) acc else HoldsView(acc.source + (hold.bookId to hold))
                    }
                    .awaitSingle()
            }
                .mapLeft { transformToSystemError(it) }
                .bind()
        }
    }

    private suspend fun findCurrentCheckoutsFor(patronId: PatronId): Either<SystemError, CheckoutsView> {
        return either {
            Either.catch {
                databaseClient.sql(
                    """
                        SELECT
                            h.book_id,
                            h.checkout_till
                        FROM checkouts_sheet h
                        WHERE h.checked_out_by_patron_id = :patronId AND h.returned_at IS NULL
                    """.trimIndent(),
                )
                    .bind("patronId", patronId.source).fetch().all()
                    .map { it.toCheckout().getOrNull() }
                    .reduce(CheckoutsView(emptyMap())) { acc, checkout ->
                        if (checkout == null) acc else CheckoutsView(acc.source + (checkout.bookId to checkout))
                    }
                    .awaitSingle()
            }
                .mapLeft { transformToSystemError(it) }
                .bind()
        }
    }

    private fun Map<String, Any>.toHold(): Either<SystemError, Hold> {
        val row = this
        return either {
            Hold(
                bookId = row.convert("book_id", BookId::from) {
                    SystemError.DataInconsistency("도서 ID 변환에 실패했습니다: ${row["book_id"]}")
                }.bind(),
                till = (row["hold_till"] as? OffsetDateTime).toOption().map { it.toInstant() },
            )
        }
    }

    private fun Map<String, Any>.toCheckout(): Either<SystemError, Checkout> {
        val row = this
        return either {
            Checkout(
                bookId = row.convert("book_id", BookId::from) {
                    SystemError.DataInconsistency("도서 ID 변환에 실패했습니다: ${row["book_id"]}")
                }.bind(),
                till = (row["checkout_till"] as? OffsetDateTime).toOption()
                    .map { it.toInstant() }
                    .getOrElse {
                        raise(SystemError.DataInconsistency("체크아웃 시각 변환에 실패했습니다: ${row["checkout_till"]}"))
                    },
            )
        }
    }

    companion object {
        private val logger = mu.KotlinLogging.logger { }
    }
}
