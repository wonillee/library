package io.chrislee.library.lending.patron.application.usecase

import arrow.core.None
import arrow.core.left
import arrow.core.right
import arrow.core.some
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.patron.application.domain.DefaultPlacingOnHoldPoliciesProvider
import io.chrislee.library.lending.patron.application.domain.Hold.Companion.asHold
import io.chrislee.library.lending.patron.application.domain.OverdueCheckouts
import io.chrislee.library.lending.patron.application.domain.Patron
import io.chrislee.library.lending.patron.application.domain.PatronHolds
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.chrislee.library.lending.patron.application.domain.PatronRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Duration
import java.time.Instant

class CancelHoldUseCaseImplTest : DescribeSpec({
    val policies = DefaultPlacingOnHoldPoliciesProvider().provide()
    val query = mockk<FindBookOnHoldQuery>()
    val patronRepository = mockk<PatronRepository>()
    val usecase = CancelHoldUseCaseImpl(query, patronRepository)

    it("도서 대여 예약자가 존재하고 그 예약자가 해당 도서에 대한 예약을 취소하는 경우 정상적으로 예약이 취소되어야 한다") {
        val patronId = PatronId.uniqueOne()
        val book = BookOnHold(
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            byPatron = patronId,
            holdTill = Instant.now().plus(Duration.ofDays(10)).some(),
        )
        val patron = Patron(
            patronId = patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds(setOf(book.bookId.asHold())),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { query.findBookOnHold(any(), any()) } returns book.some().right()
        coEvery { patronRepository.findByPatronId(any()) } returns patron.some().right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()

        val command = CancelHoldCommand(patronId, book.bookId, Instant.now())
        val event = usecase.execute(command).shouldBeRight()
        expectThat(event) {
            get { this.bookId }.isEqualTo(book.bookId)
            get { this.patronId }.isEqualTo(patronId)
        }
        coVerify(exactly = 1) { patronRepository.save(any(), any()) }
    }
    it("어떤 도서를 대여하지 않은 고객이 해당 도서에 대한 예약을 취소하는 경우 예약 취소 실패 이벤트가 발생해야 한다") {
        val patronId = PatronId.uniqueOne()
        val anotherPatronId = PatronId.uniqueOne()
        val book = BookOnHold(
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            byPatron = anotherPatronId,
            holdTill = Instant.now().plus(Duration.ofDays(10)).some(),
        )
        val patron = Patron(
            patronId = patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds.empty(),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { query.findBookOnHold(any(), any()) } returns book.some().right()
        coEvery { patronRepository.findByPatronId(any()) } returns patron.some().right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()

        val command = CancelHoldCommand(patronId, book.bookId, Instant.now())
        val event = usecase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.bookId }.isEqualTo(book.bookId)
            get { this.patronId }.isEqualTo(patronId)
        }
        coVerify(exactly = 0) { patronRepository.save(any(), any()) }
    }
    it("존재하지 않는 고객 ID인 경우 예약 취소 실패 이벤트가 발생해야 한다") {
        val patronId = PatronId.uniqueOne()
        val anotherPatronId = PatronId.uniqueOne()
        val book = BookOnHold(
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            byPatron = anotherPatronId,
            holdTill = Instant.now().plus(Duration.ofDays(10)).some(),
        )
        coEvery { query.findBookOnHold(any(), any()) } returns book.some().right()
        coEvery { patronRepository.findByPatronId(any()) } returns None.right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()

        val command = CancelHoldCommand(patronId, book.bookId, Instant.now())
        val event = usecase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.bookId }.isEqualTo(book.bookId)
            get { this.patronId }.isEqualTo(patronId)
        }
        coVerify(exactly = 0) { patronRepository.save(any(), any()) }
    }
    it("존재하지 않는 도서인 경우 예약 취소 실패 이벤트가 발생해야 한다") {
        val patronId = PatronId.uniqueOne()
        val bookId = BookId.uniqueOne()
        val patron = Patron(
            patronId = patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds.empty(),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { query.findBookOnHold(any(), any()) } returns None.right()
        coEvery { patronRepository.findByPatronId(any()) } returns patron.some().right()
        coEvery { patronRepository.save(any()) } returns mockk<Patron>().right()

        val command = CancelHoldCommand(patronId, bookId, Instant.now())
        val event = usecase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.bookId }.isEqualTo(bookId)
            get { this.patronId }.isEqualTo(patronId)
        }
        coVerify(exactly = 0) { patronRepository.save(any(), any()) }
    }
    it("고객 상태 저장이 실패하는 경우 예약 취소 실패 이벤트가 발생해야 한다") {
        val patronId = PatronId.uniqueOne()
        val book = BookOnHold(
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            byPatron = patronId,
            holdTill = Instant.now().plus(Duration.ofDays(10)).some(),
        )
        val patron = Patron(
            patronId = patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds(setOf(book.bookId.asHold())),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { query.findBookOnHold(any(), any()) } returns book.some().right()
        coEvery { patronRepository.findByPatronId(any()) } returns patron.some().right()
        coEvery { patronRepository.save(any(), any()) } returns SystemError.IOError("blah").left()

        val command = CancelHoldCommand(patronId, book.bookId, Instant.now())
        val event = usecase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.bookId }.isEqualTo(book.bookId)
            get { this.patronId }.isEqualTo(patronId)
        }
        coVerify(exactly = 1) { patronRepository.save(any(), any()) }
    }

    afterTest { clearMocks(query, patronRepository) }
})
