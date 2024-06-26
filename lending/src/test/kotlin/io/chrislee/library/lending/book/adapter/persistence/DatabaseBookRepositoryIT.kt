package io.chrislee.library.lending.book.adapter.persistence

import io.chrislee.library.common.infrastructure.DomainEventPublisherConfiguration
import io.chrislee.library.common.infrastructure.TestContainerDatabaseInitializer
import io.chrislee.library.lending.book.adapter.BookConfiguration
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent
import io.chrislee.library.lending.patron.application.domain.HoldDuration
import io.chrislee.library.lending.patron.application.domain.NumberOfDays
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.kotest.assertions.arrow.core.shouldBeNone
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.test.context.ContextConfiguration
import java.time.Duration
import java.time.Instant

@ContextConfiguration(
    classes = [
        BookConfiguration::class,
        DomainEventPublisherConfiguration::class,
    ],
    initializers = [TestContainerDatabaseInitializer::class],
)
@DataR2dbcTest
class DatabaseBookRepositoryIT {
    @Autowired
    private lateinit var bookRepository: DatabaseBookRepository

    @DisplayName("대여 도서 사이클에 따라 정상적으로 도서 상태를 저장/조회할 수 있는지 확인한다")
    @Test
    fun testBasic() {
        runTest {
            // 대여 가능 도서 등록
            val bookId = BookId.uniqueOne()
            val bookType = BookType.Restricted
            val availableBook = AvailableBook(
                bookId = bookId,
                bookType = bookType,
            )
            bookRepository.findByBookId(bookId).shouldBeRight().shouldBeNone()
            bookRepository.save(availableBook).shouldBeRight()
            bookRepository.findByBookId(BookId.from("NOT_EXIST").shouldBeRight()).shouldBeRight().shouldBeNone()
            bookRepository.findByBookId(bookId).shouldBeRight().shouldBeSome(availableBook)

            // 대여 가능 도서가 대여 예약 도서로 변경
            val patronId = PatronId.uniqueOne()
            val bookOnHold = availableBook.handle(
                BookPlacedOnHoldEvent(
                    patronId = patronId,
                    bookId = bookId,
                    bookType = bookType,
                    holdDuration = HoldDuration.OpenEnded(Instant.now()),
                ),
            ).shouldBeRight()
            bookRepository.save(bookOnHold).shouldBeRight()
            bookRepository.findByBookId(bookId).shouldBeRight().shouldBeSome(bookOnHold)

            // 대여 예약 도서가 대여 도서로 변경
            val checkedOutBook = bookOnHold.handle(
                BookCheckedOutEvent(
                    patronId = patronId,
                    bookId = bookId,
                    bookType = bookType,
                    till = Instant.now().plus(Duration.ofDays(10)),
                ),
            ).shouldBeRight()
            bookRepository.save(checkedOutBook).shouldBeRight()
            bookRepository.findByBookId(bookId).shouldBeRight().shouldBeSome(checkedOutBook)

            // 대여 도서가 대여 가능 도서로 변경
            val availableBookAgain = checkedOutBook.handle(
                BookReturnedEvent(
                    patronId = patronId,
                    bookId = bookId,
                    bookType = bookType,
                ),
            ).shouldBeRight()
            bookRepository.save(availableBookAgain).shouldBeRight()
            bookRepository.findByBookId(bookId).shouldBeRight().shouldBeSome(availableBookAgain)
        }
    }

    @DisplayName("대여 가능한 도서를 조회할 수 있어야 한다")
    @Test
    fun shouldFindAvailableBookInDatabase() {
        runTest {
            val bookId = BookId.uniqueOne()
            val availableBook = AvailableBook(
                bookId = bookId,
                bookType = BookType.Circulating,
            )
            bookRepository.findAvailableBookByBookId(bookId).shouldBeRight().shouldBeNone()
            bookRepository.save(availableBook).shouldBeRight()
            bookRepository.findAvailableBookByBookId(bookId).shouldBeRight().shouldBeSome(availableBook)

            val bookOnHold = availableBook.handle(
                BookPlacedOnHoldEvent(
                    patronId = PatronId.uniqueOne(),
                    bookId = availableBook.bookId,
                    bookType = availableBook.bookType,
                    holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(5).shouldBeRight()),
                ),
            ).shouldBeRight()
            bookRepository.save(bookOnHold).shouldBeRight()
            bookRepository.findAvailableBookByBookId(bookId).shouldBeRight().shouldBeNone()
        }
    }

    @DisplayName("대여 예약된 도서들을 조회할 수 있어야 한다")
    @Test
    fun shouldFindBookOnHoldInDatabase() {
        runTest {
            val bookId = BookId.uniqueOne()
            val patronId = PatronId.uniqueOne()
            val availableBook = AvailableBook(
                bookId = bookId,
                bookType = BookType.Circulating,
            )
            bookRepository.findBookOnHold(bookId, patronId).shouldBeRight().shouldBeNone()
            bookRepository.save(availableBook).shouldBeRight()
            bookRepository.findBookOnHold(bookId, patronId).shouldBeRight().shouldBeNone()

            val bookOnHold = availableBook.handle(
                BookPlacedOnHoldEvent(
                    patronId = patronId,
                    bookId = availableBook.bookId,
                    bookType = availableBook.bookType,
                    holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(5).shouldBeRight()),
                ),
            ).shouldBeRight()
            bookRepository.save(bookOnHold).shouldBeRight()
            bookRepository.findBookOnHold(bookId, patronId).shouldBeRight().shouldBeSome(bookOnHold)
        }
    }
}
