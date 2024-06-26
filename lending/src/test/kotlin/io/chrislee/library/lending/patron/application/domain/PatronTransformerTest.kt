package io.chrislee.library.lending.patron.application.domain

import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookType
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.time.Duration
import java.time.Instant

class PatronTransformerTest : DescribeSpec({
    val policies = DefaultPlacingOnHoldPoliciesProvider().provide()

    fun regularPatron(): Patron {
        return Patron(
            patronId = PatronId.uniqueOne(),
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds.empty(),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
    }

    it("기간 한정 대여 예약 이벤트를 반영할 수 있어야 한다") {
        val patron = regularPatron()
        val event = BookPlacedOnHoldEvent(
            patronId = patron.patronId,
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(10).shouldBeRight()),
        )
        val newPatron = PatronTransformer.transform(patron, event)
        expectThat(newPatron) {
            get { this.patronHolds.count() }.isEqualTo(1)
            get { this.patronHolds.has(event.bookId) }.isTrue()
        }
    }
    it("무기한 대여 예약 이벤트를 반영할 수 있어야 한다") {
        val patron = regularPatron().copy(patronType = Patron.Type.Researcher)
        val event = BookPlacedOnHoldEvent(
            patronId = patron.patronId,
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            holdDuration = HoldDuration.OpenEnded(Instant.now()),
        )
        val newPatron = PatronTransformer.transform(patron, event)
        expectThat(newPatron) {
            get { this.patronHolds.count() }.isEqualTo(1)
            get { this.patronHolds.has(event.bookId) }.isTrue()
        }
    }
    it("대여 이벤트를 반영할 수 있어야 한다") {
        val patron = regularPatron()
        val bookOnHoldEvent = BookPlacedOnHoldEvent(
            patronId = patron.patronId,
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(10).shouldBeRight()),
        )
        val bookCheckedOutEvent = BookCheckedOutEvent(
            patronId = patron.patronId,
            bookId = bookOnHoldEvent.bookId,
            bookType = bookOnHoldEvent.bookType,
            till = Instant.now().plus(Duration.ofDays(10)),
        )
        val patronWithBookHold = PatronTransformer.transform(patron, bookOnHoldEvent)
        expectThat(patronWithBookHold) {
            get { this.patronHolds.count() }.isEqualTo(1)
        }
        val patronWithBookCheckedOut = PatronTransformer.transform(patronWithBookHold, bookCheckedOutEvent)
        expectThat(patronWithBookCheckedOut) {
            get { this.patronHolds.count() }.isEqualTo(0)
        }
    }
    it("대여 예약 취소 이벤트를 반영할 수 있어야 한다") {
        val patron = regularPatron()
        val bookOnHoldEvent = BookPlacedOnHoldEvent(
            patronId = patron.patronId,
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(10).shouldBeRight()),
        )
        val bookHoldCancelledEvent = BookHoldCancelledEvent(
            patronId = patron.patronId,
            bookId = bookOnHoldEvent.bookId,
        )
        val patronWithBookHold = PatronTransformer.transform(patron, bookOnHoldEvent)
        expectThat(patronWithBookHold) {
            get { this.patronHolds.count() }.isEqualTo(1)
        }
        val patronWithoutBookHold = PatronTransformer.transform(patronWithBookHold, bookHoldCancelledEvent)
        expectThat(patronWithoutBookHold) {
            get { this.patronHolds.count() }.isEqualTo(0)
        }
    }
    it("대여 예약 만기 이벤트를 반영할 수 있어야 한다") {
        val patron = regularPatron()
        val bookOnHoldEvent = BookPlacedOnHoldEvent(
            patronId = patron.patronId,
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(10).shouldBeRight()),
        )
        val bookHoldExpiredEvent = BookHoldExpiredEvent(
            patronId = patron.patronId,
            bookId = bookOnHoldEvent.bookId,
        )
        val patronWithBookHold = PatronTransformer.transform(patron, bookOnHoldEvent)
        expectThat(patronWithBookHold) {
            get { this.patronHolds.count() }.isEqualTo(1)
        }
        val patronWithoutBookHold = PatronTransformer.transform(patronWithBookHold, bookHoldExpiredEvent)
        expectThat(patronWithoutBookHold) {
            get { this.patronHolds.count() }.isEqualTo(0)
        }
    }
    it("대여 기한 초과 등록 이벤트를 반영할 수 있어야 한다") {
        val patron = regularPatron()
        val event = OverdueCheckoutRegisteredEvent(
            patronId = patron.patronId,
            bookId = BookId.uniqueOne(),
        )
        val newPatron = PatronTransformer.transform(patron, event)
        expectThat(newPatron) {
            get { this.overdueCheckouts.count() }.isEqualTo(1)
            get { this.overdueCheckouts.has(event.bookId) }.isTrue()
        }
    }
    it("도서 반환 이벤트가 발생하면 대여 기한이 초과된 도서가 지워져야 한다") {
        val patron = regularPatron()
        val overdueCheckoutEvent = OverdueCheckoutRegisteredEvent(
            patronId = patron.patronId,
            bookId = BookId.uniqueOne(),
        )
        val bookReturnedEvent = BookReturnedEvent(
            occurredAt = Instant.now(),
            patronId = patron.patronId,
            bookId = overdueCheckoutEvent.bookId,
            bookType = BookType.Circulating,
        )
        val patronWithOverdueCheckout = PatronTransformer.transform(patron, overdueCheckoutEvent)
        expectThat(patronWithOverdueCheckout) {
            get { this.overdueCheckouts.count() }.isEqualTo(1)
            get { this.overdueCheckouts.has(overdueCheckoutEvent.bookId) }.isTrue()
        }
        val patronWithoutOverdueCheckout = PatronTransformer.transform(patronWithOverdueCheckout, bookReturnedEvent)
        expectThat(patronWithoutOverdueCheckout) {
            get { this.overdueCheckouts.count() }.isEqualTo(0)
            get { this.overdueCheckouts.has(overdueCheckoutEvent.bookId) }.isFalse()
        }
    }
})
