package io.chrislee.library.lending.book.application.usecase

import arrow.core.Either
import arrow.core.None
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.some
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.Book
import io.chrislee.library.lending.book.application.domain.BookDuplicateHoldFoundEvent
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.book.application.domain.BookRepository
import io.chrislee.library.lending.book.application.domain.CheckedOutBook
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent

internal class PatronEventFiredUseCasesImpl(
    private val bookRepository: BookRepository,
) : BookPlacedOnHoldEventFiredUseCase,
    BookCheckedOutEventFiredUseCase,
    BookHoldExpiredEventFiredUseCase,
    BookHoldCancelledEventFiredUseCase,
    BookReturnedEventFiredUseCase {
    override suspend fun handle(event: BookPlacedOnHoldEvent): Either<ErrorOnHandlingPatronEvent, Book> {
        return either {
            val bookFound = bookRepository.findByBookId(event.bookId)
                .mapLeft { ErrorOnHandlingPatronEvent.System(it) }.bind()
                .getOrElse { raise(ErrorOnHandlingPatronEvent.System(eventForNonExistentBook(event))) }
            val maybeBookToBeSaved = when (bookFound) {
                is BookOnHold -> {
                    if (bookFound.byPatron != event.patronId) {
                        raise(bookDuplicateHoldFoundError(bookFound, event))
                    }
                    None
                }

                is CheckedOutBook -> None
                is AvailableBook -> bookFound.handle(event)
                    .mapLeft { ErrorOnHandlingPatronEvent.InvalidState(it) }
                    .bind()
                    .some()
            }
            maybeBookToBeSaved.fold({ bookFound }) { bookOnHold ->
                bookRepository.save(bookOnHold).mapLeft { ErrorOnHandlingPatronEvent.System(it) }.bind()
                bookOnHold
            }
        }
    }

    override suspend fun handle(event: BookCheckedOutEvent): Either<ErrorOnHandlingPatronEvent, Book> {
        return either {
            val bookFound = bookRepository.findByBookId(event.bookId)
                .mapLeft { ErrorOnHandlingPatronEvent.System(it) }.bind()
                .getOrElse { raise(ErrorOnHandlingPatronEvent.System(eventForNonExistentBook(event))) }
            val maybeBookToBeSaved = when (bookFound) {
                is BookOnHold -> bookFound.handle(event)
                    .mapLeft { ErrorOnHandlingPatronEvent.InvalidState(it) }
                    .bind()
                    .some()

                else -> None
            }
            maybeBookToBeSaved.fold({ bookFound }) { checkedOutBook ->
                bookRepository.save(checkedOutBook).mapLeft { ErrorOnHandlingPatronEvent.System(it) }.bind()
                checkedOutBook
            }
        }
    }

    override suspend fun handle(event: BookHoldExpiredEvent): Either<ErrorOnHandlingPatronEvent, Book> {
        return either {
            val bookFound = bookRepository.findByBookId(event.bookId)
                .mapLeft { ErrorOnHandlingPatronEvent.System(it) }.bind()
                .getOrElse { raise(ErrorOnHandlingPatronEvent.System(eventForNonExistentBook(event))) }
            val maybeBookToBeSaved = when (bookFound) {
                is BookOnHold -> bookFound.handle(event)
                    .mapLeft { ErrorOnHandlingPatronEvent.InvalidState(it) }
                    .bind()
                    .some()

                else -> None
            }
            maybeBookToBeSaved.fold({ bookFound }) { availableBook ->
                bookRepository.save(availableBook)
                availableBook
            }
        }
    }

    override suspend fun handle(event: BookHoldCancelledEvent): Either<ErrorOnHandlingPatronEvent, Book> {
        return either {
            val bookFound = bookRepository.findByBookId(event.bookId)
                .mapLeft { ErrorOnHandlingPatronEvent.System(it) }.bind()
                .getOrElse { raise(ErrorOnHandlingPatronEvent.System(eventForNonExistentBook(event))) }
            val maybeBookToBeSaved = when (bookFound) {
                is BookOnHold -> bookFound.handle(event)
                    .mapLeft { ErrorOnHandlingPatronEvent.InvalidState(it) }
                    .bind()
                    .some()

                else -> None
            }
            maybeBookToBeSaved.fold({ bookFound }) { availableBook ->
                bookRepository.save(availableBook).mapLeft { ErrorOnHandlingPatronEvent.System(it) }.bind()
                availableBook
            }
        }
    }

    override suspend fun handle(event: BookReturnedEvent): Either<ErrorOnHandlingPatronEvent, Book> {
        return either {
            val bookFound = bookRepository.findByBookId(event.bookId)
                .mapLeft { ErrorOnHandlingPatronEvent.System(it) }.bind()
                .getOrElse { raise(ErrorOnHandlingPatronEvent.System(eventForNonExistentBook(event))) }
            val maybeBookToBeSaved = when (bookFound) {
                is CheckedOutBook -> bookFound.handle(event)
                    .mapLeft { ErrorOnHandlingPatronEvent.InvalidState(it) }
                    .bind()
                    .some()

                else -> None
            }
            maybeBookToBeSaved.fold({ bookFound }) { availableBook ->
                bookRepository.save(availableBook).mapLeft { ErrorOnHandlingPatronEvent.System(it) }.bind()
                availableBook
            }
        }
    }

    private fun eventForNonExistentBook(event: Any): SystemError {
        return SystemError.DataInconsistency("존재하지 않는 도서에 대한 이벤트가 발행되었습니다: $event")
    }

    private fun bookDuplicateHoldFoundError(
        book: BookOnHold,
        event: BookPlacedOnHoldEvent,
    ): ErrorOnHandlingPatronEvent {
        return ErrorOnHandlingPatronEvent.BookDuplicateHoldFound(
            BookDuplicateHoldFoundEvent(
                bookId = book.bookId,
                firstPatronId = book.byPatron,
                secondPatronId = event.patronId,
            ),
        )
    }
}
