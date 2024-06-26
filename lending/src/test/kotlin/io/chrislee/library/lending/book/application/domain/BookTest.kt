package io.chrislee.library.lending.book.application.domain

import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent
import io.chrislee.library.lending.patron.application.domain.CheckoutDuration
import io.chrislee.library.lending.patron.application.domain.DefaultPlacingOnHoldPoliciesProvider
import io.chrislee.library.lending.patron.application.domain.Hold.Companion.asHold
import io.chrislee.library.lending.patron.application.domain.HoldDuration
import io.chrislee.library.lending.patron.application.domain.NumberOfDays
import io.chrislee.library.lending.patron.application.domain.OverdueCheckouts
import io.chrislee.library.lending.patron.application.domain.Patron
import io.chrislee.library.lending.patron.application.domain.PatronHolds
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.core.spec.style.DescribeSpec
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import java.time.Duration
import java.time.Instant

internal class BookTest : DescribeSpec({
    describe("대여 가능한 도서") {
        it("는 잘못된 대여 예약 이벤트(도서 ID가 다름)를 받았을 경우 대여 예약된 도서로 전환 가능하지 않다") {
            val book = availableBook()
            val anotherBook = availableBook()
            val patron = regularPatron()
            val duration = closeEndedHoldDuration()
            val bookPlacedOnHoldEvent = patron.placeOnHold(book, duration)
                .shouldBeRight()
                .bookPlacedOnHoldEvent
            val error = anotherBook.handle(bookPlacedOnHoldEvent).shouldBeLeft()
            expectThat(error).get { this.message }.contains("ID")
        }
        it("는 잘못된 대여 예약 이벤트(도서 유형이 다름)를 받았을 경우 대여 예약된 도서로 전환 가능하지 않다") {
            val book = availableBook()
            val anotherBook = availableBook(book.bookId, BookType.Restricted)
            val patron = regularPatron()
            val duration = closeEndedHoldDuration()
            val bookPlacedOnHoldEvent = patron.placeOnHold(book, duration)
                .shouldBeRight()
                .bookPlacedOnHoldEvent
            val error = anotherBook.handle(bookPlacedOnHoldEvent).shouldBeLeft()
            expectThat(error).get { this.message }.contains("유형")
        }
        it("는 정상적인 대여 예약 이벤트를 받았을 경우 대여 예약된 도서로 전환되어야 한다") {
            val book = availableBook()
            val patron = regularPatron()
            val duration = closeEndedHoldDuration()
            val bookPlacedOnHoldEvent = patron.placeOnHold(book, duration)
                .shouldBeRight()
                .bookPlacedOnHoldEvent
            val bookOnHold: BookOnHold = book.handle(bookPlacedOnHoldEvent).shouldBeRight()
            expectThat(bookOnHold) {
                get { this.bookId }.isEqualTo(book.bookId)
                get { this.bookType }.isEqualTo(book.bookType)
                get { this.byPatron }.isEqualTo(patron.patronId)
                get { this.holdTill }.isEqualTo(duration.holdTill())
            }
        }
    }
    describe("대여 예약된 도서") {
        it("는 잘못된 대여 예약 취소 이벤트(도서 ID가 다름)를 받았을 경우 대여 가능한 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val anotherBookId = BookId.uniqueOne()
            val duration = closeEndedHoldDuration()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold(), anotherBookId.asHold())))
            val book = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookHoldCancelledEvent = patron.cancelHold(book).shouldBeRight()
            val anotherBook = BookOnHold(
                bookId = anotherBookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val error = anotherBook.handle(bookHoldCancelledEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("ID")
            }
        }
        it("는 잘못된 대여 예약 취소 이벤트(도서 예약자가 다름)를 받았을 경우 대여 가능한 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val anotherBookId = BookId.uniqueOne()
            val duration = closeEndedHoldDuration()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold(), anotherBookId.asHold())))
            val book = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookHoldCancelledEvent = patron.cancelHold(book).shouldBeRight()
            val anotherPatronId = PatronId.uniqueOne()
            val sameBookWithDifferentPatron = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = anotherPatronId,
                holdTill = duration.holdTill(),
            )
            val error = sameBookWithDifferentPatron.handle(bookHoldCancelledEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("예약자")
            }
        }
        it("는 정상적인 대여 예약 취소 이벤트를 받았을 경우 대여 가능한 도서로 전환되어야 한다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val book = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookHoldCancelledEvent = patron.cancelHold(book).shouldBeRight()
            val availableBook = book.handle(bookHoldCancelledEvent).shouldBeRight()
            expectThat(availableBook) {
                get { this.bookId }.isEqualTo(book.bookId)
                get { this.bookType }.isEqualTo(book.bookType)
            }
        }
        it("는 잘못된 대여 반납 이벤트(도서 유형이 다름)를 받았을 경우 대여 가능한 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val book = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookReturnedEvent = BookReturnedEvent(
                patronId = patron.patronId,
                bookId = bookId,
                bookType = BookType.Restricted,
            )
            val error = book.handle(bookReturnedEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("유형")
            }
        }
        it("는 잘못된 대여 반납 이벤트(도서 대여자가 다름)를 받았을 경우 대여 가능한 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val book = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val anotherPatronId = PatronId.uniqueOne()
            val bookReturnedEvent = BookReturnedEvent(
                patronId = anotherPatronId,
                bookId = bookId,
                bookType = book.bookType,
            )
            val error = book.handle(bookReturnedEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("대여자")
            }
        }
        it("는 정상적인 대여 반납 이벤트를 받았을 경우 대여 가능한 도서로 전환되어야 한다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val book = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookReturnedEvent = BookReturnedEvent(
                patronId = patron.patronId,
                bookId = bookId,
                bookType = book.bookType,
            )
            val availableBook = book.handle(bookReturnedEvent).shouldBeRight()
            expectThat(availableBook) {
                get { this.bookId }.isEqualTo(book.bookId)
                get { this.bookType }.isEqualTo(book.bookType)
            }
        }
        it("는 잘못된 대여 이벤트(도서 ID가 다름)를 받았을 경우 대여된 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val bookOnHold = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookCheckedOutEvent = BookCheckedOutEvent(
                patronId = patron.patronId,
                bookId = BookId.uniqueOne(),
                bookType = bookOnHold.bookType,
                till = CheckoutDuration.maxDuration(Instant.now()).till(),
            )
            val error = bookOnHold.handle(bookCheckedOutEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("ID")
            }
        }
        it("는 잘못된 대여 이벤트(도서 유형이 다름)를 받았을 경우 대여된 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val bookOnHold = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookCheckedOutEvent = BookCheckedOutEvent(
                patronId = patron.patronId,
                bookId = bookId,
                bookType = BookType.Restricted,
                till = CheckoutDuration.maxDuration(Instant.now()).till(),
            )
            val error = bookOnHold.handle(bookCheckedOutEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("유형")
            }
        }
        it("는 잘못된 대여 이벤트(도서 대여자가 다름)를 받았을 경우 대여된 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val bookOnHold = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookCheckedOutEvent = BookCheckedOutEvent(
                patronId = PatronId.uniqueOne(),
                bookId = bookId,
                bookType = bookOnHold.bookType,
                till = CheckoutDuration.maxDuration(Instant.now()).till(),
            )
            val error = bookOnHold.handle(bookCheckedOutEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("대여자")
            }
        }
        it("는 정상적인 대여 이벤트를 받았을 경우 대여된 도서로 전환되어야 한다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val bookOnHold = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookCheckedOutEvent = BookCheckedOutEvent(
                patronId = patron.patronId,
                bookId = bookId,
                bookType = bookOnHold.bookType,
                till = CheckoutDuration.maxDuration(Instant.now()).till(),
            )
            val availableBook = bookOnHold.handle(bookCheckedOutEvent).shouldBeRight()
            expectThat(availableBook) {
                get { this.bookId }.isEqualTo(bookOnHold.bookId)
                get { this.bookType }.isEqualTo(bookOnHold.bookType)
            }
        }
        it("는 잘못된 대여 예약 만기 이벤트(도서 ID가 다름)를 받았을 경우 대여 가능한 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val bookOnHold = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookHoldExpiredEvent = BookHoldExpiredEvent(
                occurredAt = duration.holdTill().shouldBeSome().plus(Duration.ofDays(1L)),
                patronId = patron.patronId,
                bookId = BookId.uniqueOne(),
            )
            val error = bookOnHold.handle(bookHoldExpiredEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("ID")
            }
        }
        it("는 잘못된 대여 예약 만기 이벤트(대여 예약자가 다름)를 받았을 경우 대여 가능한 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val bookOnHold = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookHoldExpiredEvent = BookHoldExpiredEvent(
                occurredAt = duration.holdTill().shouldBeSome().plus(Duration.ofDays(1L)),
                patronId = PatronId.uniqueOne(),
                bookId = bookId,
            )
            val error = bookOnHold.handle(bookHoldExpiredEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("예약자")
            }
        }
        it("는 정상적인 대여 예약 만기 이벤트를 받았을 경우 대여 가능한 도서로 전환되어야 한다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron(holds = PatronHolds(setOf(bookId.asHold())))
            val duration = closeEndedHoldDuration()
            val bookOnHold = BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
                holdTill = duration.holdTill(),
            )
            val bookHoldExpiredEvent = BookHoldExpiredEvent(
                occurredAt = duration.holdTill().shouldBeSome().plus(Duration.ofDays(1L)),
                patronId = patron.patronId,
                bookId = bookId,
            )
            val availableBook = bookOnHold.handle(bookHoldExpiredEvent).shouldBeRight()
            expectThat(availableBook) {
                get { this.bookId }.isEqualTo(bookOnHold.bookId)
                get { this.bookType }.isEqualTo(bookOnHold.bookType)
            }
        }
    }
    describe("대여된 도서") {
        it("는 잘못된 대여 반납 이벤트(도서 ID가 다름)를 받았을 경우 대여 가능한 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron()
            val book = CheckedOutBook(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
            )
            val bookReturnedEvent = BookReturnedEvent(
                patronId = patron.patronId,
                bookId = BookId.uniqueOne(),
                bookType = book.bookType,
            )
            val error = book.handle(bookReturnedEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("ID")
            }
        }
        it("는 잘못된 대여 반납 이벤트(도서 유형이 다름)를 받았을 경우 대여 가능한 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron()
            val book = CheckedOutBook(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
            )
            val bookReturnedEvent = BookReturnedEvent(
                patronId = patron.patronId,
                bookId = bookId,
                bookType = BookType.Restricted,
            )
            val error = book.handle(bookReturnedEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("유형")
            }
        }
        it("는 잘못된 대여 반납 이벤트(도서 ID가 다름)를 받았을 경우 대여 가능한 도서로 전환될 수 없다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron()
            val book = CheckedOutBook(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
            )
            val bookReturnedEvent = BookReturnedEvent(
                patronId = PatronId.uniqueOne(),
                bookId = bookId,
                bookType = book.bookType,
            )
            val error = book.handle(bookReturnedEvent).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("대여자")
            }
        }
        it("는 정상적인 대여 반납 이벤트를 받았을 경우 대여 가능한 도서로 전환되어야 한다") {
            val bookId = BookId.uniqueOne()
            val patron = regularPatron()
            val book = CheckedOutBook(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = patron.patronId,
            )
            val bookReturnedEvent = BookReturnedEvent(
                patronId = patron.patronId,
                bookId = bookId,
                bookType = book.bookType,
            )
            val availableBook = book.handle(bookReturnedEvent).shouldBeRight()
            expectThat(availableBook) {
                get { this.bookId }.isEqualTo(book.bookId)
                get { this.bookType }.isEqualTo(book.bookType)
            }
        }
    }
}) {
    companion object {
        private val policies = DefaultPlacingOnHoldPoliciesProvider().provide()

        fun availableBook(
            bookId: BookId = BookId.uniqueOne(),
            bookType: BookType = BookType.Circulating,
        ): AvailableBook {
            return AvailableBook(bookId, bookType)
        }

        fun regularPatron(holds: PatronHolds = PatronHolds.empty()): Patron {
            return Patron(
                PatronId.uniqueOne(),
                Patron.Type.Regular,
                policies,
                holds,
                OverdueCheckouts.empty(),
            )
        }

        fun closeEndedHoldDuration(): HoldDuration.CloseEnded {
            return HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(1).shouldBeRight())
        }
    }
}
