package io.chrislee.library.lending.patron.adapter.persistence

import io.chrislee.library.common.infrastructure.DomainEventPublisherConfiguration
import io.chrislee.library.common.infrastructure.TestContainerDatabaseInitializer
import io.chrislee.library.lending.book.adapter.BookConfiguration
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.patron.adapter.PatronConfiguration
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent
import io.chrislee.library.lending.patron.application.domain.HoldDuration
import io.chrislee.library.lending.patron.application.domain.OverdueCheckoutRegisteredEvent
import io.chrislee.library.lending.patron.application.domain.Patron
import io.chrislee.library.lending.patron.application.domain.PatronCreatedEvent
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.chrislee.library.lending.patron.application.domain.PatronRepository
import io.kotest.assertions.arrow.core.shouldBeNone
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.test.context.ContextConfiguration
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.time.Duration
import java.time.Instant

@ContextConfiguration(
    classes = [
        PatronConfiguration::class,
        BookConfiguration::class,
        DomainEventPublisherConfiguration::class,
    ],
    initializers = [TestContainerDatabaseInitializer::class],
)
@DataR2dbcTest
class DatabasePatronRepositoryIT {
    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var patronRepository: PatronRepository

    @AfterEach
    fun afterEach() {
        runTest {
            databaseClient.sql("DELETE FROM hold").fetch().awaitRowsUpdated()
            databaseClient.sql("DELETE FROM overdue_checkout").fetch().awaitRowsUpdated()
            databaseClient.sql("DELETE FROM patron").fetch().awaitRowsUpdated()
        }
    }

    @DisplayName("고객 ID로 고객 정보를 조회할 수 있어야 한다")
    @Test
    fun testFindByPatronId() {
        runTest {
            val patronId = PatronId.uniqueOne()
            databaseClient
                .sql("INSERT INTO patron (id, patron_type, patron_id) VALUES (1, 'Researcher', :patronId)")
                .bind("patronId", patronId.source)
                .fetch().awaitRowsUpdated()
            (1..3).forEach {
                databaseClient
                    .sql("INSERT INTO hold (book_id, patron_id, patron, till) VALUES (:bookId, :patronId, 1, :till)")
                    .bind("bookId", "book$it")
                    .bind("patronId", patronId.source)
                    .bind("till", Instant.now())
                    .fetch().awaitRowsUpdated()
            }
            (2..3).forEach {
                databaseClient
                    .sql("INSERT INTO overdue_checkout (book_id, patron_id, patron) VALUES (:bookId, :patronId, 1)")
                    .bind("bookId", "book$it")
                    .bind("patronId", patronId.source)
                    .fetch().awaitRowsUpdated()
            }

            val patron = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            expectThat(patron) {
                get { this.patronId }.isEqualTo(patronId)
                get { this.patronType }.isEqualTo(Patron.Type.Researcher)
                get { this.patronHolds }.and {
                    get { this.count() }.isEqualTo(3)
                    get { this.maximumHoldsAfterHolding() }.isTrue()
                }
                get { this.overdueCheckouts }.and {
                    get { this.count() }.isEqualTo(2)
                    get { this.source }.contains(BookId.from("book2").shouldBeRight())
                    get { this.source }.contains(BookId.from("book3").shouldBeRight())
                }
            }
        }
    }

    @DisplayName("고객 생성 이벤트를 처리할 수 있어야 한다")
    @Test
    fun testSaveWithPatronCreatedEvent() {
        runTest {
            val event = PatronCreatedEvent(
                patronId = PatronId.uniqueOne(),
                patronType = Patron.Type.Researcher,
            )
            patronRepository.findByPatronId(event.patronId).shouldBeRight().shouldBeNone()
            val saved = patronRepository.save(event).shouldBeRight { it.message }
            expectThat(saved) {
                get { this.patronId }.isEqualTo(event.patronId)
                get { this.patronType }.isEqualTo(event.patronType)
                get { this.patronHolds.count() }.isEqualTo(0)
                get { this.overdueCheckouts.count() }.isEqualTo(0)
            }
            patronRepository.findByPatronId(event.patronId).shouldBeRight().shouldBeSome(saved)
        }
    }

    @DisplayName("도서 대여 예약 이벤트를 처리할 수 있어야 한다")
    @Test
    fun testSaveWithBookPlacedOnHoldEvent() {
        runTest {
            val patronId = PatronId.uniqueOne()
            val book1 = BookId.from("b1").shouldBeRight()
            val book2 = BookId.from("b2").shouldBeRight()
            val book3 = BookId.from("b3").shouldBeRight()
            databaseClient
                .sql("INSERT INTO patron (id, patron_id, patron_type) VALUES (1, :patronId, :patronType)")
                .bind("patronId", patronId.source)
                .bind("patronType", Patron.Type.Researcher.name)
                .fetch().awaitRowsUpdated()
            listOf(book1, book2, book3).forEach { bookId ->
                databaseClient
                    .sql("INSERT INTO hold (book_id, patron_id, patron, till) VALUES (:bookId, :patronId, :patron, NOW())")
                    .bind("bookId", bookId.source)
                    .bind("patronId", patronId.source)
                    .bind("patron", 1)
                    .fetch().awaitRowsUpdated()
            }
            val event = BookPlacedOnHoldEvent(
                patronId = PatronId.uniqueOne(),
                bookId = BookId.from("book4").shouldBeRight(),
                bookType = BookType.Circulating,
                holdDuration = HoldDuration.OpenEnded(Instant.now()),
            )
            val patronBefore = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            patronRepository.save(patronBefore, event).shouldBeRight { it.message }
            val patronAfter = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            expectThat(patronAfter) {
                get { this.patronId }.isEqualTo(patronId)
                get { this.patronType }.isEqualTo(patronBefore.patronType)
                get { this.patronHolds.count() }.isEqualTo(4)
                get { this.patronHolds.has(event.bookId) }.isTrue()
                get { this.overdueCheckouts.count() }.isEqualTo(0)
            }
        }
    }

    @DisplayName("도서 대여 예약 취소 이벤트를 처리할 수 있어야 한다")
    @Test
    fun testSaveWithBookHoldCancelledEvent() {
        runTest {
            val patronId = PatronId.uniqueOne()
            val book1 = BookId.from("b1").shouldBeRight()
            val book2 = BookId.from("b2").shouldBeRight()
            val book3 = BookId.from("b3").shouldBeRight()
            databaseClient
                .sql("INSERT INTO patron (id, patron_id, patron_type) VALUES (1, :patronId, :patronType)")
                .bind("patronId", patronId.source)
                .bind("patronType", Patron.Type.Researcher.name)
                .fetch().awaitRowsUpdated()
            listOf(book1, book2, book3).forEach { bookId ->
                databaseClient
                    .sql("INSERT INTO hold (book_id, patron_id, patron, till) VALUES (:bookId, :patronId, :patron, NOW())")
                    .bind("bookId", bookId.source)
                    .bind("patronId", patronId.source)
                    .bind("patron", 1)
                    .fetch().awaitRowsUpdated()
            }
            val event = BookHoldCancelledEvent(
                patronId = PatronId.uniqueOne(),
                bookId = book2,
            )
            val patronBefore = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            patronRepository.save(patronBefore, event).shouldBeRight { it.message }
            val patronAfter = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            expectThat(patronAfter) {
                get { this.patronId }.isEqualTo(patronId)
                get { this.patronType }.isEqualTo(patronBefore.patronType)
                get { this.patronHolds.count() }.isEqualTo(2)
                get { this.patronHolds.has(event.bookId) }.isFalse()
                get { this.overdueCheckouts.count() }.isEqualTo(0)
            }
        }
    }

    @DisplayName("도서 대여 예약 만기 이벤트를 처리할 수 있어야 한다")
    @Test
    fun testSaveWithBookHoldExpiredEvent() {
        runTest {
            val patronId = PatronId.uniqueOne()
            val book1 = BookId.from("b1").shouldBeRight()
            val book2 = BookId.from("b2").shouldBeRight()
            val book3 = BookId.from("b3").shouldBeRight()
            databaseClient
                .sql("INSERT INTO patron (id, patron_id, patron_type) VALUES (1, :patronId, :patronType)")
                .bind("patronId", patronId.source)
                .bind("patronType", Patron.Type.Researcher.name)
                .fetch().awaitRowsUpdated()
            listOf(book1, book2, book3).forEach { bookId ->
                databaseClient
                    .sql("INSERT INTO hold (book_id, patron_id, patron, till) VALUES (:bookId, :patronId, :patron, NOW())")
                    .bind("bookId", bookId.source)
                    .bind("patronId", patronId.source)
                    .bind("patron", 1)
                    .fetch().awaitRowsUpdated()
            }
            val event = BookHoldExpiredEvent(
                patronId = PatronId.uniqueOne(),
                bookId = book2,
            )
            val patronBefore = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            patronRepository.save(patronBefore, event).shouldBeRight { it.message }
            val patronAfter = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            expectThat(patronAfter) {
                get { this.patronId }.isEqualTo(patronId)
                get { this.patronType }.isEqualTo(patronBefore.patronType)
                get { this.patronHolds.count() }.isEqualTo(2)
                get { this.patronHolds.has(event.bookId) }.isFalse()
                get { this.overdueCheckouts.count() }.isEqualTo(0)
            }
        }
    }

    @DisplayName("도서 대여 이벤트를 처리할 수 있어야 한다")
    @Test
    fun testSaveWithBookCheckedOutEvent() {
        runTest {
            val patronId = PatronId.uniqueOne()
            val book1 = BookId.from("b1").shouldBeRight()
            val book2 = BookId.from("b2").shouldBeRight()
            val book3 = BookId.from("b3").shouldBeRight()
            databaseClient
                .sql("INSERT INTO patron (id, patron_id, patron_type) VALUES (1, :patronId, :patronType)")
                .bind("patronId", patronId.source)
                .bind("patronType", Patron.Type.Researcher.name)
                .fetch().awaitRowsUpdated()
            listOf(book1, book2, book3).forEach { bookId ->
                databaseClient
                    .sql("INSERT INTO hold (book_id, patron_id, patron, till) VALUES (:bookId, :patronId, :patron, NOW())")
                    .bind("bookId", bookId.source)
                    .bind("patronId", patronId.source)
                    .bind("patron", 1)
                    .fetch().awaitRowsUpdated()
            }
            val event = BookCheckedOutEvent(
                patronId = PatronId.uniqueOne(),
                bookId = book2,
                bookType = BookType.Circulating,
                till = Instant.now().plus(Duration.ofDays(10)),
            )
            val patronBefore = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            patronRepository.save(patronBefore, event).shouldBeRight { it.message }
            val patronAfter = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            expectThat(patronAfter) {
                get { this.patronId }.isEqualTo(patronId)
                get { this.patronType }.isEqualTo(patronBefore.patronType)
                get { this.patronHolds.count() }.isEqualTo(2)
                get { this.patronHolds.has(event.bookId) }.isFalse()
                get { this.overdueCheckouts.count() }.isEqualTo(0)
            }
        }
    }

    @DisplayName("도서 반납 이벤트를 처리할 수 있어야 한다")
    @Test
    fun testSaveWithBookReturnedEvent() {
        runTest {
            val patronId = PatronId.uniqueOne()
            val book1 = BookId.from("b1").shouldBeRight()
            val book2 = BookId.from("b2").shouldBeRight()
            val book3 = BookId.from("b3").shouldBeRight()
            databaseClient
                .sql("INSERT INTO patron (id, patron_id, patron_type) VALUES (1, :patronId, :patronType)")
                .bind("patronId", patronId.source)
                .bind("patronType", Patron.Type.Researcher.name)
                .fetch().awaitRowsUpdated()
            listOf(book1, book2, book3).forEach { bookId ->
                databaseClient
                    .sql("INSERT INTO hold (book_id, patron_id, patron, till) VALUES (:bookId, :patronId, :patron, NOW())")
                    .bind("bookId", bookId.source)
                    .bind("patronId", patronId.source)
                    .bind("patron", 1)
                    .fetch().awaitRowsUpdated()
            }
            val event = BookReturnedEvent(
                occurredAt = Instant.now(),
                patronId = PatronId.uniqueOne(),
                bookId = BookId.from("book4").shouldBeRight(),
                bookType = BookType.Circulating,
            )
            val patronBefore = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            patronRepository.save(patronBefore, event).shouldBeRight { it.message }
            val patronAfter = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            expectThat(patronAfter) {
                get { this.patronId }.isEqualTo(patronId)
                get { this.patronType }.isEqualTo(patronBefore.patronType)
                get { this.patronHolds.count() }.isEqualTo(3)
                get { this.patronHolds.has(event.bookId) }.isFalse()
                get { this.overdueCheckouts.count() }.isEqualTo(0)
            }
        }
    }

    @DisplayName("도서 대여 기간 초과 등록 이벤트를 처리할 수 있어야 한다")
    @Test
    fun testSaveWithOverdueCheckoutRegisteredEvent() {
        runTest {
            val patronId = PatronId.uniqueOne()
            val book1 = BookId.from("b1").shouldBeRight()
            val book2 = BookId.from("b2").shouldBeRight()
            val book3 = BookId.from("b3").shouldBeRight()
            databaseClient
                .sql("INSERT INTO patron (id, patron_id, patron_type) VALUES (1, :patronId, :patronType)")
                .bind("patronId", patronId.source)
                .bind("patronType", Patron.Type.Researcher.name)
                .fetch().awaitRowsUpdated()
            listOf(book1, book2, book3).forEach { bookId ->
                databaseClient
                    .sql("INSERT INTO hold (book_id, patron_id, patron, till) VALUES (:bookId, :patronId, :patron, NOW())")
                    .bind("bookId", bookId.source)
                    .bind("patronId", patronId.source)
                    .bind("patron", 1)
                    .fetch().awaitRowsUpdated()
            }
            val event = OverdueCheckoutRegisteredEvent(
                patronId = PatronId.uniqueOne(),
                bookId = BookId.from("book4").shouldBeRight(),
            )
            val patronBefore = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            patronRepository.save(patronBefore, event).shouldBeRight { it.message }
            val patronAfter = patronRepository.findByPatronId(patronId).shouldBeRight().shouldBeSome()
            expectThat(patronAfter) {
                get { this.patronId }.isEqualTo(patronId)
                get { this.patronType }.isEqualTo(patronBefore.patronType)
                get { this.patronHolds.count() }.isEqualTo(3)
                get { this.patronHolds.has(event.bookId) }.isFalse()
                get { this.overdueCheckouts.count() }.isEqualTo(1)
                get { this.overdueCheckouts.has(event.bookId) }.isTrue()
            }
        }
    }
}
