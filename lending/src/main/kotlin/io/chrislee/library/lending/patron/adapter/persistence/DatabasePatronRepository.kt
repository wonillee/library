package io.chrislee.library.lending.patron.adapter.persistence

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.some
import arrow.core.toOption
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.common.infrastructure.R2DBCColumnToDomainConverter.convert
import io.chrislee.library.common.infrastructure.transformToSystemError
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvents
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent
import io.chrislee.library.lending.patron.application.domain.Hold.Companion.asHold
import io.chrislee.library.lending.patron.application.domain.OverdueCheckoutRegisteredEvent
import io.chrislee.library.lending.patron.application.domain.OverdueCheckouts
import io.chrislee.library.lending.patron.application.domain.Patron
import io.chrislee.library.lending.patron.application.domain.PatronCreatedEvent
import io.chrislee.library.lending.patron.application.domain.PatronEvent
import io.chrislee.library.lending.patron.application.domain.PatronHolds
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.chrislee.library.lending.patron.application.domain.PatronRepository
import io.chrislee.library.lending.patron.application.domain.PatronTransformer
import io.chrislee.library.lending.patron.application.domain.PlacingOnHoldPoliciesProvider
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import java.time.Instant
import java.time.OffsetDateTime

internal class DatabasePatronRepository(
    private val databaseClient: DatabaseClient,
    private val r2dbcPatronRepository: R2DBCPatronRepository,
    private val r2dbcHoldRepository: R2DBCHoldRepository,
    private val r2dbcOverdueCheckoutRepository: R2DBCOverdueCheckoutRepository,
    private val placingOnHoldPoliciesProvider: PlacingOnHoldPoliciesProvider,
) : PatronRepository {
    override suspend fun findByPatronId(patronId: PatronId): Either<SystemError, Option<Patron>> {
        return either {
            Either.catch {
                fetchByPatronId(patronId).bind().map { entity ->
                    Patron(
                        patronId = entity.patronId,
                        patronType = entity.patronType,
                        placingOnHoldPolicies = placingOnHoldPoliciesProvider.provide(),
                        patronHolds = PatronHolds(entity.booksOnHold.map { it.bookId.asHold() }.toSet()),
                        overdueCheckouts = OverdueCheckouts(entity.overdueCheckouts.map { it.bookId }.toSet()),
                    )
                }
            }
                .mapLeft { transformToSystemError(it) }
                .bind()
        }
    }

    override suspend fun save(event: PatronCreatedEvent): Either<SystemError, Patron> {
        return Either.catch {
            r2dbcPatronRepository.save(
                PatronEntity(
                    null,
                    event.patronId,
                    event.patronType,
                    emptySet(),
                    emptySet(),
                ),
            )
        }
            .mapLeft { transformToSystemError(it) }
            .map {
                Patron(
                    it.patronId,
                    it.patronType,
                    placingOnHoldPoliciesProvider.provide(),
                    PatronHolds.empty(),
                    OverdueCheckouts.empty(),
                )
            }
    }

    override suspend fun save(currentPatron: Patron, event: PatronEvent): Either<SystemError, Patron> {
        val patronId = currentPatron.patronId
        return either {
            Either.catch {
                val patronToBeSaved = when (event) {
                    is BookCheckedOutEvent -> PatronTransformer.transform(currentPatron, event)
                    is BookHoldCancelledEvent -> PatronTransformer.transform(currentPatron, event)
                    is BookHoldExpiredEvent -> PatronTransformer.transform(currentPatron, event)
                    is BookPlacedOnHoldEvent -> PatronTransformer.transform(currentPatron, event)
                    is BookReturnedEvent -> PatronTransformer.transform(currentPatron, event)
                    is OverdueCheckoutRegisteredEvent -> PatronTransformer.transform(currentPatron, event)
                    is BookPlacedOnHoldEvents -> raise(SystemError.DataInconsistency("분할되어야 하는 이벤트"))
                    else -> raise(SystemError.DataInconsistency("잘못된 메소드 호출: $event"))
                }
                val currentEntity = fetchByPatronId(patronId).bind()
                    .getOrElse { raise(SystemError.DataInconsistency("존재하지 않는 고객: $patronId")) }
                val patronSystemId = currentEntity.id as Int
                ensure(currentPatron.patronType == currentEntity.patronType) {
                    SystemError.DataInconsistency("입력 받은 고객 유형과 저장소 고객 유형이 일치하지 않습니다")
                }
                val holdsToBeRemoved = currentEntity.booksOnHold.mapNotNull {
                    if (patronToBeSaved.patronHolds.has(it.bookId)) {
                        null
                    } else {
                        HoldEntity(
                            id = it.id,
                            patronId = patronId,
                            patron = patronSystemId,
                            bookId = it.bookId,
                            till = it.till,
                        )
                    }
                }
                val holdsToBeAdded = patronToBeSaved.patronHolds.asSequnce().mapNotNull {
                    if (currentPatron.patronHolds.has(it.source)) {
                        null
                    } else {
                        HoldEntity(
                            id = null,
                            patronId = patronId,
                            patron = patronSystemId,
                            bookId = it.source,
                            till = Instant.now(),
                        )
                    }
                }
                r2dbcHoldRepository.saveAll(holdsToBeAdded.toSet()).collect()
                r2dbcHoldRepository.deleteAll(holdsToBeRemoved.toSet())
                val checkoutsToBeRemoved = currentEntity.overdueCheckouts.mapNotNull {
                    if (patronToBeSaved.overdueCheckouts.has(it.bookId)) {
                        null
                    } else {
                        OverdueCheckoutEntity(
                            id = it.id,
                            patronId = patronId,
                            patron = patronSystemId,
                            bookId = it.bookId,
                        )
                    }
                }
                val checkoutsToBeAdded = patronToBeSaved.overdueCheckouts.asSequence().mapNotNull {
                    if (currentPatron.patronHolds.has(it)) {
                        null
                    } else {
                        OverdueCheckoutEntity(
                            id = null,
                            patronId = patronId,
                            patron = patronSystemId,
                            bookId = it,
                        )
                    }
                }
                r2dbcOverdueCheckoutRepository.saveAll(checkoutsToBeAdded.toSet()).collect()
                r2dbcOverdueCheckoutRepository.deleteAll(checkoutsToBeRemoved.toSet())
                patronToBeSaved
            }
                .mapLeft { transformToSystemError(it) }.bind()
        }
    }

    private suspend fun fetchByPatronId(patronId: PatronId): Either<SystemError, Option<PatronEntity>> {
        return either {
            val rowsFetchSpec = Either.catch {
                databaseClient.sql(
                    """
                        SELECT
                            p.id,
                            p.patron_id,
                            p.patron_type,
                            h.id AS h_id,
                            h.book_id AS hold,
                            h.till,
                            NULL AS o_id,
                            NULL AS overdue_checkout
                        FROM patron p
                        LEFT JOIN hold h ON p.id = h.patron
                        WHERE p.patron_id = :patronId
                        UNION ALL
                        SELECT
                            p.id,
                            p.patron_id,
                            p.patron_type,
                            NULL AS h_id,
                            NULL AS hold,
                            NULL AS till,
                            o.id AS o_id,
                            o.book_id AS overdue_checkout
                        FROM patron p
                        LEFT JOIN overdue_checkout o ON p.id = o.patron
                        WHERE p.patron_id = :patronId
                    """.trimIndent(),
                ).bind("patronId", patronId.source).fetch().all()
            }
                .mapLeft { transformToSystemError(it) }.bind()
            rowsFetchSpec
                .reduce(nonExistentPatron(patronId)) { entity, row -> patronMapper(entity, row).bind() }
                .awaitSingleOrNull().toOption().flatMap { it.noneIfNonExistent() }
        }
    }

    private fun nonExistentPatron(patronId: PatronId): PatronEntity {
        return PatronEntity(null, patronId, Patron.Type.Regular)
    }

    private fun PatronEntity.noneIfNonExistent(): Option<PatronEntity> {
        return if (this.id == null) None else this.some()
    }

    private fun dataInconsistency(reason: String): SystemError.DataInconsistency {
        return SystemError.DataInconsistency("patron 매핑 중 오류 발생: $reason")
    }

    private fun patronMapper(
        patronEntity: PatronEntity,
        row: MutableMap<String, Any>,
    ): Either<SystemError, PatronEntity> {
        return either {
            PatronEntity(
                id = (row["id"] as? Int) ?: raise(dataInconsistency("patron.id(${row["id"]})")),
                patronId = patronEntity.patronId,
                patronType = Either.catch { Patron.Type.valueOf(row["patron_type"] as String) }
                    .mapLeft { dataInconsistency("patron.patronType(${row["patron_type"]})") }.bind(),
                booksOnHold = patronEntity.booksOnHold + holdMapper(patronEntity.patronId, row),
                overdueCheckouts = patronEntity.overdueCheckouts + overdueCheckoutsMapper(patronEntity.patronId, row),
            )
        }
    }

    private fun holdMapper(patronId: PatronId, row: Map<String, Any>): Set<HoldEntity> {
        return if (row["id"] is Int && row["h_id"] is Int) {
            val maybeBookId = row.convert("hold", BookId::from) { dataInconsistency("") }.getOrNull().toOption()
            maybeBookId.fold({ emptySet() }) { bookId ->
                setOf(
                    HoldEntity(
                        row["h_id"] as Int,
                        patronId,
                        row["id"] as Int,
                        bookId,
                        (row["till"] as? OffsetDateTime)?.toInstant(),
                    ),
                )
            }
        } else {
            emptySet()
        }
    }

    private fun overdueCheckoutsMapper(patronId: PatronId, row: MutableMap<String, Any>): Set<OverdueCheckoutEntity> {
        return if (row["id"] is Int && row["o_id"] is Int) {
            val maybeBookId = row.convert("overdue_checkout", BookId::from) { dataInconsistency("") }
                .getOrNull().toOption()
            maybeBookId.fold({ emptySet() }) { bookId ->
                setOf(
                    OverdueCheckoutEntity(
                        row["o_id"] as Int,
                        patronId,
                        row["id"] as Int,
                        bookId,
                    ),
                )
            }
        } else {
            emptySet()
        }
    }
}
