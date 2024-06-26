package io.chrislee.library.lending.patron.application.usecase

import arrow.core.None
import arrow.core.right
import arrow.core.some
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.patron.application.domain.DefaultPlacingOnHoldPoliciesProvider
import io.chrislee.library.lending.patron.application.domain.Hold.Companion.asHold
import io.chrislee.library.lending.patron.application.domain.HoldDuration
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

class PlacingOnHoldUseCaseImplTest : DescribeSpec({
    val policies = DefaultPlacingOnHoldPoliciesProvider().provide()
    val query = mockk<FindAvailableBookQuery>()
    val patronRepository = mockk<PatronRepository>()
    val usecase = PlacingOnHoldUseCaseImpl(query, patronRepository)
    val numberOfDays = NumberOfDays.of(10).shouldBeRight()
    val duration = HoldDuration.CloseEnded.of(Instant.now(), numberOfDays)

    it("고객이 존재하고 대여 가능한 도서에 대한 대여 예약을 하는 경우 정상적으로 예약되어야 한다") {
        val patronId = PatronId.uniqueOne()
        val book = AvailableBook(
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
        )
        val patron = Patron(
            patronId = patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds.empty(),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { query.findAvailableBookByBookId(any()) } returns book.some().right()
        coEvery { patronRepository.findByPatronId(any()) } returns patron.some().right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()

        val command = PlacingOnHoldCommand(patronId, book.bookId, numberOfDays.some(), duration.from)
        val event = usecase.execute(command).shouldBeRight()
        expectThat(event) {
            get { this.patronId }.isEqualTo(patronId)
            get { this.bookPlacedOnHoldEvent }.and {
                get { this.bookId }.isEqualTo(book.bookId)
                get { this.patronId }.isEqualTo(patronId)
                get { this.bookType }.isEqualTo(book.bookType)
                get { this.holdDuration.holdTill() }.isEqualTo(duration.holdTill())
            }
        }
        coVerify(exactly = 1) { patronRepository.save(any(), any()) }
    }
    it("도메인 정책을 위반한 경우(최대 권수 이상의 책을 대여 예약) 예약 실패 이벤트가 발생해야 한다") {
        val patronId = PatronId.uniqueOne()
        val book = AvailableBook(
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
        )
        val patron = Patron(
            patronId = patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds(
                setOf(
                    BookId.uniqueOne().asHold(),
                    BookId.uniqueOne().asHold(),
                    BookId.uniqueOne().asHold(),
                    BookId.uniqueOne().asHold(),
                ),
            ),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { query.findAvailableBookByBookId(any()) } returns book.some().right()
        coEvery { patronRepository.findByPatronId(any()) } returns patron.some().right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()

        val command = PlacingOnHoldCommand(patronId, book.bookId, numberOfDays.some(), Instant.now())
        val event = usecase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.patronId }.isEqualTo(patronId)
            get { this.bookId }.isEqualTo(book.bookId)
        }
        coVerify(exactly = 0) { patronRepository.save(any(), any()) }
    }
    it("존재하지 않는 고객 ID인 경우 예약 실패 이벤트가 발생해야 한다") {
        val patronId = PatronId.uniqueOne()
        val book = AvailableBook(
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
        )
        coEvery { query.findAvailableBookByBookId(any()) } returns book.some().right()
        coEvery { patronRepository.findByPatronId(any()) } returns None.right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()

        val command = PlacingOnHoldCommand(patronId, book.bookId, numberOfDays.some(), Instant.now())
        val event = usecase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.patronId }.isEqualTo(patronId)
            get { this.bookId }.isEqualTo(book.bookId)
        }
        coVerify(exactly = 0) { patronRepository.save(any(), any()) }
    }
    it("존재하지 않는 도서인 경우 예약 실패 이벤트가 발생해야 한다") {
        val patronId = PatronId.uniqueOne()
        val bookId = BookId.uniqueOne()
        val patron = Patron(
            patronId = patronId,
            patronType = Patron.Type.Regular,
            placingOnHoldPolicies = policies,
            patronHolds = PatronHolds.empty(),
            overdueCheckouts = OverdueCheckouts.empty(),
        )
        coEvery { query.findAvailableBookByBookId(any()) } returns None.right()
        coEvery { patronRepository.findByPatronId(any()) } returns patron.some().right()
        coEvery { patronRepository.save(any(), any()) } returns mockk<Patron>().right()

        val command = PlacingOnHoldCommand(patronId, bookId, numberOfDays.some(), Instant.now())
        val event = usecase.execute(command).shouldBeLeft()
        expectThat(event) {
            get { this.patronId }.isEqualTo(patronId)
            get { this.bookId }.isEqualTo(bookId)
        }
        coVerify(exactly = 0) { patronRepository.save(any(), any()) }
    }

    afterTest { clearMocks(query, patronRepository) }
})
