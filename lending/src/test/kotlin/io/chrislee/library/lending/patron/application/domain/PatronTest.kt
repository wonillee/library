package io.chrislee.library.lending.patron.application.domain

import arrow.core.None
import arrow.core.Some
import arrow.core.some
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.book.application.domain.CheckedOutBook
import io.chrislee.library.lending.patron.application.domain.Hold.Companion.asHold
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import java.time.Duration
import java.time.Instant

internal class PatronTest : DescribeSpec({
    describe("모든 도서 대여자") {
        val patrons = listOf(regularPatron(), researcherPatron())
        it("는 정상적인 상황에서 대여한 도서를 반납할 수 있다") {
            patrons.forEach { patron ->
                val book = CheckedOutBook(
                    bookId = BookId.uniqueOne(),
                    bookType = BookType.Circulating,
                    byPatron = patron.patronId,
                )
                val bookReturnedEvent = patron.returnBook(book).shouldBeRight()
                expectThat(bookReturnedEvent) {
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookId }.isEqualTo(book.bookId)
                    get { this.bookType }.isEqualTo(book.bookType)
                }
            }
        }
        it("는 연체 상황에서 대여한 도서를 반납할 수 있다") {
            val bookId = BookId.uniqueOne()
            val overduePatrons = listOf(
                regularPatron(overdueCheckouts = OverdueCheckouts(setOf(bookId))),
                researcherPatron(overdueCheckouts = OverdueCheckouts(setOf(bookId))),
            )
            overduePatrons.forEach { patron ->
                val book = CheckedOutBook(
                    bookId = bookId,
                    bookType = BookType.Circulating,
                    byPatron = patron.patronId,
                )
                val bookReturnedEvent = patron.returnBook(book).shouldBeRight()
                expectThat(bookReturnedEvent) {
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookId }.isEqualTo(book.bookId)
                    get { this.bookType }.isEqualTo(book.bookType)
                }
            }
        }
        it("는 기간 한정 대여 예약을 할 수 있다") {
            val book = availableBook(BookType.Circulating)
            val holdDuration = closedEndHoldDuration()
            patrons.forEach { patron ->
                val event = patron.placeOnHold(book, holdDuration)
                val bookPlacedOnHoldEvents = event.shouldBeRight()
                expectThat(bookPlacedOnHoldEvents) {
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookPlacedOnHoldEvent }.and {
                        get { this.bookId }.isEqualTo(book.bookId)
                        get { this.bookType }.isEqualTo(book.bookType)
                        get { this.holdDuration }.isEqualTo(holdDuration)
                    }
                    get { this.maximumNumberOnHoldsReachedEvent }.isEqualTo(None)
                }
            }
        }
        it("는 기간 한정 예약 시 0 혹은 음의 양의 날짜로 예약할 수 없다") {
            NumberOfDays.of(0).shouldBeLeft()
            NumberOfDays.of(-1).shouldBeLeft()
            NumberOfDays.of(1).shouldBeRight()
        }
        it("는 대여 예약을 하지 않은 도서를 체크아웃할 수 없다") {
            val bookOnHold = bookOnHold()
            listOf(regularPatron(), researcherPatron()).forEach { patron ->
                val duration = CheckoutDuration.maxDuration(Instant.now())
                val bookCheckingOutFailedEvent = patron.checkOut(bookOnHold, duration).shouldBeLeft()
                expectThat(bookCheckingOutFailedEvent) {
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookId }.isEqualTo(bookOnHold.bookId)
                }
            }
        }
        it("는 대여 예약을 한 도서를 체크아웃할 수 있다") {
            val bookOnHold = bookOnHold()
            val holds = PatronHolds(setOf(bookOnHold.bookId.asHold()))
            listOf(regularPatron(bookOnHold.byPatron, holds), researcherPatron(bookOnHold.byPatron, holds))
                .forEach { patron ->
                    val duration = CheckoutDuration.maxDuration(Instant.now())
                    val bookCheckedOutEvent = patron.checkOut(bookOnHold, duration).shouldBeRight()
                    expectThat(bookCheckedOutEvent) {
                        get { this.patronId }.isEqualTo(patron.patronId)
                        get { this.bookId }.isEqualTo(bookOnHold.bookId)
                        get { this.bookType }.isEqualTo(bookOnHold.bookType)
                        get { this.till }.isEqualTo(duration.till())
                    }
                }
        }
        it("는 최장 60일 간 도서를 체크아웃할 수 있다") {
            (1..60).forEach {
                CheckoutDuration.of(Instant.now(), NumberOfDays.of(it).shouldBeRight()).shouldBeRight()
            }
        }
        it("는 60일을 초과해서 도서를 체크아웃할 수 없다") {
            (61..100).forEach {
                CheckoutDuration.of(Instant.now(), NumberOfDays.of(it).shouldBeRight()).shouldBeLeft()
            }
        }
        it("는 도서 대여 예약을 취소할 수 있어야 한다") {
            val book = bookOnHold()
            val holds = PatronHolds(setOf(book.bookId.asHold()))
            listOf(regularPatron(book.byPatron, holds), researcherPatron(book.byPatron, holds)).forEach { patron ->
                val bookHoldCancelledEvent = patron.cancelHold(book).shouldBeRight()
                expectThat(bookHoldCancelledEvent) {
                    get { this.patronId }.isEqualTo(book.byPatron)
                    get { this.bookId }.isEqualTo(book.bookId)
                }
            }
        }
        it("는 대여 예약하지 않은 도서를 취소할 수 없다") {
            // "모든 도서 대여자는 다른 사람이 대여 예약한 도서를 예약할 수 없다" 시나리오를 포함한다
            val book = bookOnHold()
            listOf(regularPatron(), researcherPatron()).forEach { patron ->
                val bookHoldCancellingFailedEvent = patron.cancelHold(book).shouldBeLeft()
                expectThat(bookHoldCancellingFailedEvent) {
                    get { this.patronId }.isNotEqualTo(book.byPatron)
                    get { this.bookId }.isEqualTo(book.bookId)
                }
            }
        }
    }
    describe("정규 도서 대여자") {
        it("는 기간 무제한 예약을 할 수 없다") {
            val book = availableBook(BookType.Circulating)
            val patron = regularPatron()
            val bookHoldFailedEvent = patron.placeOnHold(book, HoldDuration.OpenEnded(Instant.now()))
                .shouldBeLeft()
            expectThat(bookHoldFailedEvent) {
                get { this.patronId }.isEqualTo(patron.patronId)
                get { this.bookId }.isEqualTo(book.bookId)
                get { this.reason }.contains("기간 무제한 예약")
            }
        }
        it("는 제한된 책을 대여 예약할 수 없다") {
            val restrictedBook = availableBook(BookType.Restricted)
            val patron = regularPatron()
            val event = patron.placeOnHold(restrictedBook, closedEndHoldDuration())
            val bookHoldFailedEvent = event.shouldBeLeft()
            expectThat(bookHoldFailedEvent) {
                get { this.patronId }.isEqualTo(patron.patronId)
                get { this.bookId }.isEqualTo(restrictedBook.bookId)
                get { reason }.contains("정규 도서 대여자").contains("제한된 책")
            }
        }
        it("는 4권 이하의 책을 대여 예약할 수 있다") {
            val hold: () -> Hold = { BookId.uniqueOne().asHold() }
            val holdCases = listOf(
                PatronHolds(emptySet()),
                PatronHolds(setOf(hold())),
                PatronHolds(setOf(hold(), hold())),
                PatronHolds(setOf(hold(), hold(), hold())),
            )
            val aBook = availableBook(BookType.Circulating)
            val holdDuration = closedEndHoldDuration()
            holdCases.forEach { patronHolds ->
                val patron = regularPatron(holds = patronHolds)
                val event = patron.placeOnHold(
                    aBook,
                    holdDuration,
                )
                val bookPlacedOnHoldEvents = event.shouldBeRight()
                expectThat(bookPlacedOnHoldEvents) {
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookPlacedOnHoldEvent }.and {
                        get { this.bookId }.isEqualTo(aBook.bookId)
                        get { this.bookType }.isEqualTo(aBook.bookType)
                        get { this.holdDuration }.isEqualTo(holdDuration)
                    }
                }
            }
        }
        it("는 5권 이상의 책을 대여 예약할 수 없다") {
            val fourHolds = PatronHolds(
                setOf(
                    BookId.uniqueOne().asHold(),
                    BookId.uniqueOne().asHold(),
                    BookId.uniqueOne().asHold(),
                    BookId.uniqueOne().asHold(),
                ),
            )
            val holdDuration = closedEndHoldDuration()
            val event = regularPatron(holds = fourHolds).placeOnHold(availableBook(BookType.Circulating), holdDuration)
            val bookHoldFailedEvent = event.shouldBeLeft()
            expectThat(bookHoldFailedEvent) {
                get { reason }.contains("정규 도서 대여자").contains("최대 4개")
            }
        }
        it("는 자신이 예약할 수 있는 최대치(4권째)를 예약했다는 이벤트를 발생해야 한다") {
            val threeHolds = PatronHolds(
                setOf(
                    BookId.uniqueOne().asHold(),
                    BookId.uniqueOne().asHold(),
                    BookId.uniqueOne().asHold(),
                ),
            )
            val holdDuration = closedEndHoldDuration()
            val patron = regularPatron(holds = threeHolds)
            val book = availableBook(BookType.Circulating)
            val event = patron.placeOnHold(book, holdDuration)
            val bookPlacedOnHoldEvents = event.shouldBeRight()
            expectThat(bookPlacedOnHoldEvents) {
                get { this.patronId }.isEqualTo(patron.patronId)
                get { this.bookPlacedOnHoldEvent }.and {
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookId }.isEqualTo(book.bookId)
                    get { this.bookType }.isEqualTo(book.bookType)
                    get { this.holdDuration }.isEqualTo(holdDuration)
                }
                get { this.maximumNumberOnHoldsReachedEvent }.isA<Some<MaximumNumberOfHoldsReachedEvent>>()
            }
        }
        it("는 반납 기간을 초과한 대여 도서가 2권 이상 있는 경우 도서 대여 예약을 할 수 없다") {
            val book = availableBook(BookType.Circulating)
            val overdueCheckOutBooks = setOf(BookId.uniqueOne(), BookId.uniqueOne())
            val patron = regularPatron(overdueCheckouts = OverdueCheckouts(overdueCheckOutBooks))
            val duration = closedEndHoldDuration()
            val bookHoldFailedEvent = patron.placeOnHold(book, duration)
                .shouldBeLeft()
            expectThat(bookHoldFailedEvent) {
                get { this.bookId }.isEqualTo(book.bookId)
                get { this.patronId }.isEqualTo(patron.patronId)
                get { this.reason }.contains("반납 기간을 초과")
            }
        }
        it("는 반납 기간을 초과한 대여 도서가 2권 미만 있는 경우 도서 대여 예약을 할 수 있다") {
            val book = availableBook(BookType.Circulating)
            val overdueCheckOutBooks = setOf(BookId.uniqueOne())
            val patron = regularPatron(overdueCheckouts = OverdueCheckouts(overdueCheckOutBooks))
            val duration = closedEndHoldDuration()
            val bookPlacedOnHoldEvents = patron.placeOnHold(book, duration)
                .shouldBeRight()
            expectThat(bookPlacedOnHoldEvents) {
                get { this.patronId }.isEqualTo(patron.patronId)
                get { this.bookPlacedOnHoldEvent }.and {
                    get { this.bookId }.isEqualTo(book.bookId)
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookType }.isEqualTo(book.bookType)
                    get { this.holdDuration }.isEqualTo(duration)
                }
            }
        }
    }
    describe("연구원 도서 대여자") {
        it("는 유통 도서와 제한 도서 모두를 대여 예약할 수 있다") {
            val books = listOf(availableBook(BookType.Restricted), availableBook(BookType.Circulating))
            books.forEach { book ->
                val patron = researcherPatron()
                val holdDuration = closedEndHoldDuration()
                val bookPlaceOnHoldEvents = patron.placeOnHold(book, holdDuration).shouldBeRight()
                expectThat(bookPlaceOnHoldEvents) {
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookPlacedOnHoldEvent }.and {
                        get { this.patronId }.isEqualTo(patron.patronId)
                        get { this.bookId }.isEqualTo(book.bookId)
                        get { this.bookType }.isEqualTo(book.bookType)
                        get { this.holdDuration }.isEqualTo(holdDuration)
                    }
                }
            }
        }
        it("는 기간 무제한 대여 예약을 할 수 있다") {
            val patron = researcherPatron()
            val book = availableBook(BookType.Restricted)
            val holdDuration = HoldDuration.OpenEnded(Instant.now())
            val bookPlaceOnHoldEvents = patron.placeOnHold(book, holdDuration).shouldBeRight()
            expectThat(bookPlaceOnHoldEvents) {
                get { this.patronId }.isEqualTo(patron.patronId)
                get { this.bookPlacedOnHoldEvent }.and {
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookId }.isEqualTo(book.bookId)
                    get { this.bookType }.isEqualTo(book.bookType)
                    get { this.holdDuration }.isEqualTo(holdDuration)
                }
            }
        }
        it("는 개수 제약 없이 대여 예약을 할 수 있다") {
            listOf(0, 1, 2, 3, 4, 5, 10000).forEach { count ->
                val holds = mutableSetOf<Hold>()
                repeat(count) { holds.add(BookId.uniqueOne().asHold()) }
                val patron = researcherPatron(holds = PatronHolds(holds))
                val book = availableBook(BookType.Restricted)
                val holdDuration = HoldDuration.OpenEnded(Instant.now())
                val bookPlaceOnHoldEvents = patron.placeOnHold(book, holdDuration).shouldBeRight()
                expectThat(bookPlaceOnHoldEvents) {
                    get { this.patronId }.isEqualTo(patron.patronId)
                    get { this.bookPlacedOnHoldEvent }.and {
                        get { this.patronId }.isEqualTo(patron.patronId)
                        get { this.bookId }.isEqualTo(book.bookId)
                        get { this.bookType }.isEqualTo(book.bookType)
                        get { this.holdDuration }.isEqualTo(holdDuration)
                    }
                }
            }
        }
    }
}) {
    companion object {
        private val policies = DefaultPlacingOnHoldPoliciesProvider().provide()

        fun availableBook(bookType: BookType): AvailableBook {
            return AvailableBook(
                bookId = BookId.uniqueOne(),
                bookType = bookType,
            )
        }

        fun bookOnHold(bookId: BookId = BookId.uniqueOne()): BookOnHold {
            return BookOnHold(
                bookId = bookId,
                bookType = BookType.Circulating,
                byPatron = PatronId.uniqueOne(),
                holdTill = Instant.now().plus(Duration.ofDays(1)).some(),
            )
        }

        fun closedEndHoldDuration(): HoldDuration.CloseEnded {
            val from: Instant = Instant.MIN
            val days = NumberOfDays.of(3).shouldBeRight()
            return HoldDuration.CloseEnded.of(from, days)
        }

        fun regularPatron(
            patronId: PatronId = PatronId.uniqueOne(),
            holds: PatronHolds = PatronHolds.empty(),
            overdueCheckouts: OverdueCheckouts = OverdueCheckouts.empty(),
        ): Patron {
            return Patron(
                patronId = patronId,
                patronType = Patron.Type.Regular,
                placingOnHoldPolicies = policies,
                patronHolds = holds,
                overdueCheckouts = overdueCheckouts,
            )
        }

        fun researcherPatron(
            patronId: PatronId = PatronId.uniqueOne(),
            holds: PatronHolds = PatronHolds.empty(),
            overdueCheckouts: OverdueCheckouts = OverdueCheckouts.empty(),
        ): Patron {
            return Patron(
                patronId = patronId,
                patronType = Patron.Type.Researcher,
                placingOnHoldPolicies = policies,
                patronHolds = holds,
                overdueCheckouts = overdueCheckouts,
            )
        }
    }
}
