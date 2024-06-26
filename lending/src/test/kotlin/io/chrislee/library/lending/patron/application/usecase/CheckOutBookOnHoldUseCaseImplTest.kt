package io.chrislee.library.lending.patron.application.usecase

import arrow.core.None
import arrow.core.left
import arrow.core.right
import arrow.core.some
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.patron.application.domain.CheckoutDuration
import io.chrislee.library.lending.patron.application.domain.DefaultPlacingOnHoldPoliciesProvider
import io.chrislee.library.lending.patron.application.domain.Hold.Companion.asHold
import io.chrislee.library.lending.patron.application.domain.NumberOfDays
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
import java.time.Instant

class CheckOutBookOnHoldUseCaseImplTest : DescribeSpec({
    val policies = DefaultPlacingOnHoldPoliciesProvider().provide()
    val query = mockk<FindBookOnHoldQuery>()
    val patronRepository = mockk<PatronRepository>()
    val useCase = CheckOutBookOnHoldUseCaseImpl(query, patronRepository)
    val durationOfTenDays = CheckoutDuration.of(Instant.now(), NumberOfDays.of(10).shouldBeRight()).shouldBeRight()

    it("도서 대여자가 존재하고 해당 도서를 대여하려고 하면 성공해야 한다") {
        val command = CheckOutBookOnHoldCommand(
            patronId = PatronId.uniqueOne(),
            bookId = BookId.uniqueOne(),
            checkoutDuration = durationOfTenDays,
        )
        val book = BookOnHold(
            bookId = command.bookId,
            bookType = BookType.Circulating,
            byPatron = command.patronId,
            holdTill = durationOfTenDays.till().some(),
        )
        val patron = Patron(
            patronId = command.patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds(setOf(book.bookId.asHold())),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { patronRepository.findByPatronId(command.patronId) } returns patron.some().right()
        coEvery { query.findBookOnHold(command.bookId, command.patronId) } returns book.some().right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()
        val event = useCase.execute(command).shouldBeRight()
        expectThat(event) {
            get { this.bookId }.isEqualTo(command.bookId)
            get { this.bookType }.isEqualTo(book.bookType)
            get { this.patronId }.isEqualTo(command.patronId)
            get { this.till }.isEqualTo(durationOfTenDays.till())
        }
        coVerify(exactly = 1) { patronRepository.save(any(), any()) }
    }

    it("고객과 대여 예약된 도서는 존재하지만 다른 고객이 해당 도서를 예약한 경우 이 고객의 대여 시도는 실패해야 한다") {
        val command = CheckOutBookOnHoldCommand(
            patronId = PatronId.uniqueOne(),
            bookId = BookId.uniqueOne(),
            checkoutDuration = durationOfTenDays,
        )
        val anotherPatronId = PatronId.uniqueOne()
        val book = BookOnHold(
            bookId = command.bookId,
            bookType = BookType.Circulating,
            byPatron = anotherPatronId,
            holdTill = durationOfTenDays.till().some(),
        )
        val patron = Patron(
            patronId = command.patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds.empty(),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { patronRepository.findByPatronId(command.patronId) } returns patron.some().right()
        coEvery { query.findBookOnHold(command.bookId, command.patronId) } returns book.some().right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()
        val event = useCase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.bookId }.isEqualTo(command.bookId)
            get { this.patronId }.isEqualTo(command.patronId)
        }
        coVerify(exactly = 0) { patronRepository.save(any(), any()) }
    }
    it("도서는 존재하지만 고객이 존재하지 않는 경우 대여 시도는 실패해야 한다") {
        val command = CheckOutBookOnHoldCommand(
            patronId = PatronId.uniqueOne(),
            bookId = BookId.uniqueOne(),
            checkoutDuration = durationOfTenDays,
        )
        val anotherPatronId = PatronId.uniqueOne()
        val book = BookOnHold(
            bookId = command.bookId,
            bookType = BookType.Circulating,
            byPatron = anotherPatronId,
            holdTill = durationOfTenDays.till().some(),
        )
        coEvery { patronRepository.findByPatronId(command.patronId) } returns None.right()
        coEvery { query.findBookOnHold(command.bookId, command.patronId) } returns book.some().right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()
        val event = useCase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.bookId }.isEqualTo(command.bookId)
            get { this.patronId }.isEqualTo(command.patronId)
        }
        coVerify(exactly = 0) { patronRepository.save(any(), any()) }
    }
    it("고객은 존재하지만 도서가 존재하지 않는 경우 대여 시도는 실패해야 한다") {
        val command = CheckOutBookOnHoldCommand(
            patronId = PatronId.uniqueOne(),
            bookId = BookId.uniqueOne(),
            checkoutDuration = durationOfTenDays,
        )
        val patron = Patron(
            patronId = command.patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds.empty(),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { patronRepository.findByPatronId(command.patronId) } returns patron.some().right()
        coEvery { query.findBookOnHold(command.bookId, command.patronId) } returns None.right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()
        val event = useCase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.bookId }.isEqualTo(command.bookId)
            get { this.patronId }.isEqualTo(command.patronId)
        }
        coVerify(exactly = 0) { patronRepository.save(any(), any()) }
    }
    it("고객, 도서 상태 저장에 실패하는 경우 대여 시도는 실패해야 한다") {
        val command = CheckOutBookOnHoldCommand(
            patronId = PatronId.uniqueOne(),
            bookId = BookId.uniqueOne(),
            checkoutDuration = durationOfTenDays,
        )
        val book = BookOnHold(
            bookId = command.bookId,
            bookType = BookType.Circulating,
            byPatron = command.patronId,
            holdTill = durationOfTenDays.till().some(),
        )
        val patron = Patron(
            patronId = command.patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds(setOf(book.bookId.asHold())),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { patronRepository.findByPatronId(command.patronId) } returns patron.some().right()
        coEvery { query.findBookOnHold(command.bookId, command.patronId) } returns book.some().right()
        coEvery { patronRepository.save(any(), any()) } returns SystemError.IOError("DB 오류").left()
        val event = useCase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.bookId }.isEqualTo(command.bookId)
            get { this.patronId }.isEqualTo(command.patronId)
        }
        coVerify(exactly = 1) { patronRepository.save(any(), any()) }
    }

    afterTest { clearMocks(query, patronRepository) }
})
