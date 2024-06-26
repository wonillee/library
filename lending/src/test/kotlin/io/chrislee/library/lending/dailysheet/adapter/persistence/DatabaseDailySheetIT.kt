package io.chrislee.library.lending.dailysheet.adapter.persistence

import io.chrislee.library.common.infrastructure.DomainEventPublisherConfiguration
import io.chrislee.library.common.infrastructure.TestContainerDatabaseInitializer
import io.chrislee.library.lending.book.adapter.BookConfiguration
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.dailysheet.adapter.DailySheetConfiguration
import io.chrislee.library.lending.patron.adapter.PatronConfiguration
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent
import io.chrislee.library.lending.patron.application.domain.HoldDuration
import io.chrislee.library.lending.patron.application.domain.NumberOfDays
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.kotest.assertions.arrow.core.shouldBeRight
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.AutoConfigurationPackage
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.test.context.ContextConfiguration
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@ContextConfiguration(
    classes = [
        DailySheetConfiguration::class,
        PatronConfiguration::class,
        BookConfiguration::class,
        DomainEventPublisherConfiguration::class,
    ],
    initializers = [TestContainerDatabaseInitializer::class],
)
@DataR2dbcTest
@AutoConfigurationPackage
class DatabaseDailySheetIT {
    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @AfterEach
    fun afterEach() {
        runTest {
            databaseClient.sql("DELETE FROM holds_sheet").fetch().awaitRowsUpdated()
            databaseClient.sql("DELETE FROM checkouts_sheet").fetch().awaitRowsUpdated()
        }
    }

    @DisplayName("대여 예약 만기 조회가 정상 동작해야 한다")
    @Test
    fun queryForHoldsToExpireShouldFindExpiredHolds() {
        val now = Instant.now()
        val dailySheet = dailySheet(now)
        runTest {
            val currentNumberOfExpiredHolds = dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count()
            val holdExpired = placedOnHoldWithCloseEnded(now.minus(Duration.ofDays(2)), 1)
            val holdNotExpired = placedOnHoldWithCloseEnded(now, 2)
            dailySheet.handle(holdExpired)
            dailySheet.handle(holdNotExpired)
            expectThat(dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count())
                .isEqualTo(currentNumberOfExpiredHolds + 1)
        }
    }

    @DisplayName("대여 예약 만기 이벤트 중복 처리 시 대여 예약 만기 조회에서 멱등성을 갖춰야 한다")
    @Test
    fun handlingPlacedOnHoldShouldBeIdempotent() {
        val now = Instant.now()
        val dailySheet = dailySheet(now)
        runTest {
            val currentNumberOfExpiredHolds = dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count()
            val holdExpired = placedOnHoldWithCloseEnded(now.minus(Duration.ofDays(2)), 1)
            repeat(2) { dailySheet.handle(holdExpired) }
            expectThat(dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count())
                .isEqualTo(currentNumberOfExpiredHolds + 1)
        }
    }

    @DisplayName("대여 예약 만기 조회 시 무기한 대여 예약이 조회되지 않아야 한다")
    @Test
    fun queryForHoldsToExpireShouldNeverFindOpenEndedHolds() {
        val now = Instant.now()
        val dailySheet = dailySheet(now)
        runTest {
            val currentNumberOfExpiredHolds = dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count()
            val holdWithOpenEnded = BookPlacedOnHoldEvent(
                occurredAt = now,
                patronId = PatronId.uniqueOne(),
                bookId = BookId.uniqueOne(),
                bookType = BookType.Circulating,
                holdDuration = HoldDuration.OpenEnded(now),
            )
            dailySheet.handle(holdWithOpenEnded)
            expectThat(dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count())
                .isEqualTo(currentNumberOfExpiredHolds)
        }
    }

    @DisplayName("대여 예약 만기 조회 시 취소된 예약이 조회되지 않아야 한다")
    @Test
    fun queryForHoldsToExpireShouldNeverFindCancelledHolds() {
        val now = Instant.now()
        val dailySheet = dailySheet(now)
        runTest {
            val currentNumberOfExpiredHolds = dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count()
            val holdExpired = placedOnHoldWithCloseEnded(now.minus(Duration.ofDays(2)), 1)
            val holdCancelled = BookHoldCancelledEvent(
                occurredAt = now,
                patronId = holdExpired.patronId,
                bookId = holdExpired.bookId,
            )
            dailySheet.handle(holdExpired)
            dailySheet.handle(holdCancelled)
            expectThat(dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count())
                .isEqualTo(currentNumberOfExpiredHolds)
        }
    }

    @DisplayName("대여 에약 만기 조회 시 무기한 대여 예약에 대한 예약 만기 이벤트가 발생하더라도 이를 조회할 수 없어야 한다")
    @Test
    fun queryForHoldsToExpireShouldNeverFindAlreadyExpiredHolds() {
        val now = Instant.now()
        val dailySheet = dailySheet(now)
        runTest {
            val currentNumberOfExpiredHolds = dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count()
            val holdWithOpenEnded = BookPlacedOnHoldEvent(
                occurredAt = now,
                patronId = PatronId.uniqueOne(),
                bookId = BookId.uniqueOne(),
                bookType = BookType.Circulating,
                holdDuration = HoldDuration.OpenEnded(now),
            )
            val holdExpired = BookHoldExpiredEvent(
                occurredAt = now,
                patronId = holdWithOpenEnded.patronId,
                bookId = holdWithOpenEnded.bookId,
            )
            dailySheet.handle(holdWithOpenEnded)
            dailySheet.handle(holdExpired)
            expectThat(dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count())
                .isEqualTo(currentNumberOfExpiredHolds)
        }
    }

    @DisplayName("대여 예약 만기 조회 시 예약 만기가 된 도서를 대여하는 경우에도 이를 조회할 수 없어야 한다")
    @Test
    fun queryForHoldsToExpireShouldNeverFindAlreadyCheckedOutHolds() {
        val now = Instant.now()
        val dailySheet = dailySheet(now)
        runTest {
            val currentNumberOfExpiredHolds = dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count()
            val holdExpired = placedOnHoldWithCloseEnded(now.minus(Duration.ofDays(2)), 1)
            val bookCheckedOut = BookCheckedOutEvent(
                occurredAt = now,
                patronId = holdExpired.patronId,
                bookId = holdExpired.bookId,
                bookType = BookType.Circulating,
                till = now.plus(Duration.ofDays(10)),
            )
            dailySheet.handle(holdExpired)
            dailySheet.handle(bookCheckedOut)
            expectThat(dailySheet.queryForHoldsToExpireSheet().shouldBeRight().count())
                .isEqualTo(currentNumberOfExpiredHolds)
        }
    }

    @DisplayName("대여 기간 초과 조회가 정상 동작해야 한다")
    @Test
    fun queryForCheckoutsToOverdueShouldFindOverdueCheckouts() {
        val now = Instant.now()
        val dailySheet = dailySheet(now)
        runTest {
            val currentNumberOfOverdueCheckouts = dailySheet.queryForCheckoutsToOverdue().shouldBeRight().count()
            val overdueYesterday = BookCheckedOutEvent(
                occurredAt = now.minus(Duration.ofDays(2)),
                patronId = PatronId.uniqueOne(),
                bookId = BookId.uniqueOne(),
                bookType = BookType.Circulating,
                till = now.minus(Duration.ofDays(1)),
            )
            val overdueAtTomorrow = BookCheckedOutEvent(
                occurredAt = now.minus(Duration.ofDays(2)),
                patronId = PatronId.uniqueOne(),
                bookId = BookId.uniqueOne(),
                bookType = BookType.Circulating,
                till = now.plus(Duration.ofDays(1)),
            )
            dailySheet.handle(overdueYesterday)
            dailySheet.handle(overdueAtTomorrow)
            expectThat(dailySheet.queryForCheckoutsToOverdue().shouldBeRight { it.message }.count())
                .isEqualTo(currentNumberOfOverdueCheckouts + 1)
        }
    }

    @DisplayName("대여 기간 초과 이벤트 중복 처리 시 대여 기간 초과 조회에서 멱등성을 갖춰야 한다")
    @Test
    fun handlingBookCheckedOutEventShouldBeIdempotent() {
        val now = Instant.now()
        val dailySheet = dailySheet(now)
        runTest {
            val currentNumberOfOverdueCheckouts = dailySheet.queryForCheckoutsToOverdue().shouldBeRight().count()
            val overdueYesterday = BookCheckedOutEvent(
                occurredAt = now.minus(Duration.ofDays(2)),
                patronId = PatronId.uniqueOne(),
                bookId = BookId.uniqueOne(),
                bookType = BookType.Circulating,
                till = now.minus(Duration.ofDays(1)),
            )
            repeat(2) { dailySheet.handle(overdueYesterday) }
            expectThat(dailySheet.queryForCheckoutsToOverdue().shouldBeRight { it.message }.count())
                .isEqualTo(currentNumberOfOverdueCheckouts + 1)
        }
    }

    @DisplayName("대여 기간 초과 조회 시 이미 반납된 도서에 대해 조회되지 않아야 한다")
    @Test
    fun queryForCheckoutsToOverdueShouldNeverFindReturnedBook() {
        val now = Instant.now()
        val dailySheet = dailySheet(now)
        runTest {
            val currentNumberOfOverdueCheckouts = dailySheet.queryForCheckoutsToOverdue().shouldBeRight().count()
            val overdueYesterday = BookCheckedOutEvent(
                occurredAt = now.minus(Duration.ofDays(2)),
                patronId = PatronId.uniqueOne(),
                bookId = BookId.uniqueOne(),
                bookType = BookType.Circulating,
                till = now.minus(Duration.ofDays(1)),
            )
            val returned = BookReturnedEvent(
                occurredAt = now,
                patronId = overdueYesterday.patronId,
                bookId = overdueYesterday.bookId,
                bookType = BookType.Circulating,
            )
            dailySheet.handle(overdueYesterday)
            dailySheet.handle(returned)
            expectThat(dailySheet.queryForCheckoutsToOverdue().shouldBeRight { it.message }.count())
                .isEqualTo(currentNumberOfOverdueCheckouts)
        }
    }

    private fun dailySheet(now: Instant?): DatabaseDailySheetReadModel {
        val dailySheet = DatabaseDailySheetReadModel(databaseClient, Clock.fixed(now, ZoneId.of("UTC")))
        return dailySheet
    }

    private fun placedOnHoldWithCloseEnded(then: Instant, numberOfDays: Int): BookPlacedOnHoldEvent {
        return BookPlacedOnHoldEvent(
            occurredAt = then,
            patronId = PatronId.uniqueOne(),
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            holdDuration = HoldDuration.CloseEnded.of(
                then,
                NumberOfDays.of(numberOfDays).shouldBeRight(),
            ),
        )
    }
}
