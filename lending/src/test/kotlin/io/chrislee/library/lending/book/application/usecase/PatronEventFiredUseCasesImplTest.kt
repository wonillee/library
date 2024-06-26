package io.chrislee.library.lending.book.application.usecase

import arrow.core.None
import arrow.core.right
import arrow.core.some
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.BookDuplicateHoldFoundEvent
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.book.application.domain.BookRepository
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.book.application.domain.CheckedOutBook
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent
import io.chrislee.library.lending.patron.application.domain.HoldDuration
import io.chrislee.library.lending.patron.application.domain.NumberOfDays
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.time.Duration
import java.time.Instant

class PatronEventFiredUseCasesImplTest : DescribeSpec({
    val bookRepository = mockk<BookRepository>()
    val impl = PatronEventFiredUseCasesImpl(bookRepository)

    describe("도서가 대여 예약되었다는 이벤트를 처리할 때") {
        val bookOnHold = BookOnHold(
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            byPatron = PatronId.uniqueOne(),
            holdTill = Instant.now().plus(Duration.ofDays(10)).some(),
        )
        it("해당하는 도서 ID로 도서 정보를 찾을 수 없는 경우 시스템 오류를 발생시켜야 한다") {
            coEvery { bookRepository.findByBookId(any()) } returns None.right()
            val event = BookPlacedOnHoldEvent(
                patronId = PatronId.uniqueOne(),
                bookId = bookOnHold.bookId,
                bookType = bookOnHold.bookType,
                holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(5).shouldBeRight()),
            )
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.System>().and {
                get { this.systemError.message }.contains("존재하지 않는 도서")
            }
        }
        it("해당 도서가 이미 대여 중인 경우 아무 동작을 하지 않아야 한다") {
            val checkedOutBook = mockk<CheckedOutBook>()
            every { checkedOutBook.bookId } returns BookId.uniqueOne()
            every { checkedOutBook.bookType } returns BookType.Circulating
            coEvery { bookRepository.findByBookId(any()) } returns checkedOutBook.some().right()
            val event = BookPlacedOnHoldEvent(
                patronId = PatronId.uniqueOne(),
                bookId = checkedOutBook.bookId,
                bookType = checkedOutBook.bookType,
                holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(5).shouldBeRight()),
            )
            val book = impl.handle(event).shouldBeRight()
            expectThat(book).isEqualTo(checkedOutBook)
            coVerify(exactly = 0) { bookRepository.save(any()) }
        }
        it("다른 이가 이미 대여 예약을 했을 경우 중복 대여 예약되었다는 이벤트를 발생시켜야 한다") {
            coEvery { bookRepository.findByBookId(any()) } returns bookOnHold.some().right()
            val anotherPatronId = PatronId.uniqueOne()
            val event = BookPlacedOnHoldEvent(
                patronId = anotherPatronId,
                bookId = bookOnHold.bookId,
                bookType = bookOnHold.bookType,
                holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(5).shouldBeRight()),
            )
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.BookDuplicateHoldFound>().and {
                get { this.eventToRaise }.isA<BookDuplicateHoldFoundEvent>().and {
                    get { this.bookId }.isEqualTo(bookOnHold.bookId)
                    get { this.firstPatronId }.isEqualTo(bookOnHold.byPatron)
                    get { this.secondPatronId }.isEqualTo(anotherPatronId)
                }
            }
            coVerify(exactly = 0) { bookRepository.save(any()) }
        }
        it("같은 이가 이미 대여 예약을 했을 경우 중복 대여 예약되었다는 이벤트를 발생시키지 말아야 한다") {
            coEvery { bookRepository.findByBookId(any()) } returns bookOnHold.some().right()
            val samePatronId = bookOnHold.byPatron
            val event = BookPlacedOnHoldEvent(
                patronId = samePatronId,
                bookId = bookOnHold.bookId,
                bookType = bookOnHold.bookType,
                holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(5).shouldBeRight()),
            )
            val book = impl.handle(event).shouldBeRight()
            expectThat(book).isEqualTo(bookOnHold)
            coVerify(exactly = 0) { bookRepository.save(any()) }
        }
        it("대여 예약이 없을 경우 대여 예약이 되어야 한다") {
            val availableBook = AvailableBook(
                bookId = BookId.uniqueOne(),
                bookType = BookType.Circulating,
            )
            coEvery { bookRepository.findByBookId(any()) } returns availableBook.some().right()
            coEvery { bookRepository.save(any()) } returns Unit.right()
            val holdDuration = HoldDuration.CloseEnded.of(Instant.now(), NumberOfDays.of(5).shouldBeRight())
            val event = BookPlacedOnHoldEvent(
                patronId = PatronId.uniqueOne(),
                bookId = availableBook.bookId,
                bookType = availableBook.bookType,
                holdDuration = holdDuration,
            )
            val book = impl.handle(event).shouldBeRight()
            expectThat(book).isA<BookOnHold>().and {
                get { this.bookId }.isEqualTo(availableBook.bookId)
                get { this.bookType }.isEqualTo(availableBook.bookType)
                get { this.byPatron }.isEqualTo(event.patronId)
                get { this.holdTill }.isEqualTo(holdDuration.holdTill())
            }
            coVerify(exactly = 1) { bookRepository.save(any()) }
        }
    }

    describe("도서가 대여되었다는 이벤트를 처리할 때") {
        val event = BookCheckedOutEvent(
            patronId = PatronId.uniqueOne(),
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
            till = Instant.now().plus(Duration.ofDays(10)),
        )
        it("해당하는 도서 ID로 도서 정보를 찾을 수 없는 경우 시스템 오류를 발생시켜야 한다") {
            coEvery { bookRepository.findByBookId(any()) } returns None.right()
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.System>().and {
                get { this.systemError.message }.contains("존재하지 않는")
            }
        }
        it("다른 이가 대여 예약하고 있는 도서인 경우 상태 오류를 발생시켜야 한다") {
            val bookOnHoldByOtherPatron = BookOnHold(
                bookId = event.bookId,
                bookType = event.bookType,
                byPatron = PatronId.uniqueOne(),
                holdTill = Instant.now().plus(Duration.ofDays(10)).some(),
            )
            coEvery { bookRepository.findByBookId(any()) } returns bookOnHoldByOtherPatron.some().right()
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.InvalidState>().and {
                get { this.invalidStateError.message }.contains("대여자")
            }
        }
        it("같은 이가 대여 예약하고 있는 도서인 경우 정상적으로 도서의 상태를 대여된 도서로 전환되어야 한다") {
            val bookOnHoldBySamePatron = BookOnHold(
                bookId = event.bookId,
                bookType = event.bookType,
                byPatron = event.patronId,
                holdTill = Instant.now().plus(Duration.ofDays(10)).some(),
            )
            coEvery { bookRepository.findByBookId(any()) } returns bookOnHoldBySamePatron.some().right()
            coEvery { bookRepository.save(any()) } returns Unit.right()
            val book = impl.handle(event).shouldBeRight()
            expectThat(book).isA<CheckedOutBook>().and {
                get { this.bookId }.isEqualTo(event.bookId)
                get { this.bookType }.isEqualTo(event.bookType)
                get { this.byPatron }.isEqualTo(event.patronId)
            }
        }
    }

    describe("도서 대여 예약 만기 이벤트를 처리할 때") {
        val event = BookHoldExpiredEvent(
            patronId = PatronId.uniqueOne(),
            bookId = BookId.uniqueOne(),
        )
        it("해당하는 도서 ID로 도서 정보를 찾을 수 없는 경우 시스템 오류를 발생시켜야 한다") {
            coEvery { bookRepository.findByBookId(any()) } returns None.right()
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.System>().and {
                get { this.systemError.message }.contains("존재하지 않는")
            }
        }
        it("다른 이가 대여 예약하고 있는 도서인 경우 상태 오류를 발생시켜야 한다") {
            val bookOnHoldByOtherPatron = BookOnHold(
                bookId = event.bookId,
                bookType = BookType.Circulating,
                byPatron = PatronId.uniqueOne(),
                holdTill = Instant.now().minus(Duration.ofDays(1)).some(),
            )
            coEvery { bookRepository.findByBookId(any()) } returns bookOnHoldByOtherPatron.some().right()
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.InvalidState>().and {
                get { this.invalidStateError.message }.contains("예약자")
            }
        }
        it("같은 이가 대여 예약하고 있는 도서인 경우 정상적으로 도서의 상태를 대여 가능한 도서로 바꿔야 한다") {
            val bookOnHoldBySamePatron = BookOnHold(
                bookId = event.bookId,
                bookType = BookType.Circulating,
                byPatron = event.patronId,
                holdTill = Instant.now().plus(Duration.ofDays(10)).some(),
            )
            coEvery { bookRepository.findByBookId(any()) } returns bookOnHoldBySamePatron.some().right()
            coEvery { bookRepository.save(any()) } returns Unit.right()
            val book = impl.handle(event).shouldBeRight()
            expectThat(book).isA<AvailableBook>().and {
                get { this.bookId }.isEqualTo(event.bookId)
                get { this.bookType }.isEqualTo(BookType.Circulating)
            }
        }
    }

    describe("도서 대여 예약 취소 이벤트를 처리할 때") {
        val event = BookHoldCancelledEvent(
            bookId = BookId.uniqueOne(),
            patronId = PatronId.uniqueOne(),
        )
        it("해당하는 도서 ID로 도서 정보를 찾을 수 없는 경우 시스템 오류를 발생시켜야 한다") {
            coEvery { bookRepository.findByBookId(any()) } returns None.right()
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.System>().and {
                get { this.systemError.message }.contains("존재하지 않는")
            }
        }
        it("다른 이가 대여 예약하고 있는 도서인 경우 상태 오류를 발생시켜야 한다") {
            val bookOnHoldByOtherPatron = BookOnHold(
                bookId = event.bookId,
                bookType = BookType.Circulating,
                byPatron = PatronId.uniqueOne(),
                holdTill = Instant.now().minus(Duration.ofDays(1)).some(),
            )
            coEvery { bookRepository.findByBookId(any()) } returns bookOnHoldByOtherPatron.some().right()
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.InvalidState>().and {
                get { this.invalidStateError.message }.contains("예약자")
            }
        }
        it("같은 이가 대여 예약하고 있는 도서인 경우 정상적으로 도서의 상태를 대여 가능한 도서로 바꿔야 한다") {
            val bookOnHoldBySamePatron = BookOnHold(
                bookId = event.bookId,
                bookType = BookType.Circulating,
                byPatron = event.patronId,
                holdTill = Instant.now().plus(Duration.ofDays(10)).some(),
            )
            coEvery { bookRepository.findByBookId(any()) } returns bookOnHoldBySamePatron.some().right()
            coEvery { bookRepository.save(any()) } returns Unit.right()
            val book = impl.handle(event).shouldBeRight()
            expectThat(book).isA<AvailableBook>().and {
                get { this.bookId }.isEqualTo(event.bookId)
                get { this.bookType }.isEqualTo(BookType.Circulating)
            }
        }
    }

    describe("도서 반납 이벤트를 처리할 때") {
        val event = BookReturnedEvent(
            occurredAt = Instant.now(),
            patronId = PatronId.uniqueOne(),
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
        )
        it("해당하는 도서 ID로 도서 정보를 찾을 수 없는 경우 시스템 오류를 발생시켜야 한다") {
            coEvery { bookRepository.findByBookId(any()) } returns None.right()
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.System>().and {
                get { this.systemError.message }.contains("존재하지 않는")
            }
        }
        it("다른 이가 대여하고 있는 도서인 경우 상태 오류를 발생시켜야 한다") {
            val bookCheckedOutByOtherPatron = CheckedOutBook(
                bookId = event.bookId,
                bookType = BookType.Circulating,
                byPatron = PatronId.uniqueOne(),
            )
            coEvery { bookRepository.findByBookId(any()) } returns bookCheckedOutByOtherPatron.some().right()
            val error = impl.handle(event).shouldBeLeft()
            expectThat(error).isA<ErrorOnHandlingPatronEvent.InvalidState>().and {
                get { this.invalidStateError.message }.contains("대여자")
            }
        }
        it("같은 이가 대여하고 있는 도서인 경우 정상적으로 도서의 상태를 대여 가능한 도서로 바꿔야 한다") {
            val bookCheckedOutBySamePatron = CheckedOutBook(
                bookId = event.bookId,
                bookType = BookType.Circulating,
                byPatron = event.patronId,
            )
            coEvery { bookRepository.findByBookId(any()) } returns bookCheckedOutBySamePatron.some().right()
            coEvery { bookRepository.save(any()) } returns Unit.right()
            val book = impl.handle(event).shouldBeRight()
            expectThat(book).isA<AvailableBook>().and {
                get { this.bookId }.isEqualTo(event.bookId)
                get { this.bookType }.isEqualTo(BookType.Circulating)
            }
        }
    }

    afterContainer { clearMocks(bookRepository) }
})
