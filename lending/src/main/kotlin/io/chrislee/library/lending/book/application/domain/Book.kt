package io.chrislee.library.lending.book.application.domain

import arrow.core.Either
import arrow.core.Option
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.chrislee.library.common.domain.InvalidStateError
import io.chrislee.library.common.domain.NewType
import io.chrislee.library.lending.patron.application.domain.BookCheckedOutEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldCancelledEvent
import io.chrislee.library.lending.patron.application.domain.BookHoldExpiredEvent
import io.chrislee.library.lending.patron.application.domain.BookPlacedOnHoldEvent
import io.chrislee.library.lending.patron.application.domain.BookReturnedEvent
import io.chrislee.library.lending.patron.application.domain.PatronId
import java.time.Instant
import java.util.UUID

@JvmInline
internal value class BookId private constructor(override val source: String) : NewType<String> {
    companion object {
        fun uniqueOne(): BookId = BookId(UUID.randomUUID().toString())

        fun from(source: String): Either<InvalidStateError, BookId> {
            return either {
                ensure(source.trim().isNotBlank()) { InvalidStateError("도서 ID가 비어 있습니다") }
                BookId(source.trim())
            }
        }
    }
}

internal enum class BookType {
    Restricted,
    Circulating,
}

internal sealed interface Book {
    val bookId: BookId
    val bookType: BookType
}

internal data class AvailableBook(override val bookId: BookId, override val bookType: BookType) : Book {
    fun handle(event: BookPlacedOnHoldEvent): Either<InvalidStateError, BookOnHold> {
        return either {
            ensure(event.bookId == bookId) {
                InvalidStateError("예약하려는 도서 ID(${event.bookId})가 현재 도서 ID($bookId)와 다릅니다.")
            }
            ensure(event.bookType == bookType) {
                // TODO 도서 유형이 변경되는 비즈니스 요건이 없지만 일관성 유지를 위해 체크한다.
                //  이벤트 정보에 도서 유형 정보가 필요한지 고민해봐야 한다.
                InvalidStateError("예약하려는 도서 유형(${event.bookType})이 현재 도서 유형($bookType)과 다릅니다.")
            }
            BookOnHold(
                bookId = bookId,
                bookType = bookType,
                byPatron = event.patronId,
                holdTill = event.holdDuration.holdTill(),
            )
        }
    }
}

internal data class BookOnHold(
    override val bookId: BookId,
    override val bookType: BookType,
    val byPatron: PatronId,
    val holdTill: Option<Instant>,
) : Book {
    fun handle(event: BookHoldCancelledEvent): Either<InvalidStateError, AvailableBook> {
        return either {
            ensure(event.bookId == bookId) {
                InvalidStateError("예약 취소하려는 도서 ID(${event.bookId})가 현재 도서 ID($bookId)와 다릅니다.")
            }
            ensure(event.patronId == byPatron) {
                InvalidStateError("예약 취소하려는 도서의 예약자(${event.patronId})가 현재 도서 예약자($byPatron)와 다릅니다.")
            }
            AvailableBook(
                bookId = bookId,
                bookType = bookType,
            )
        }
    }

    fun handle(event: BookCheckedOutEvent): Either<InvalidStateError, CheckedOutBook> {
        return either {
            ensure(event.bookId == bookId) {
                InvalidStateError("대여하려는 도서 ID(${event.bookId})가 현재 도서 ID($bookId)와 다릅니다.")
            }
            ensure(event.bookType == bookType) {
                InvalidStateError("대여하려는 도서 유형(${event.bookType})이 현재 도서 유형($bookType)과 다릅니다.")
            }
            ensure(event.patronId == byPatron) {
                InvalidStateError("대여하려는 도서 대여자(${event.patronId})가 현재 도서 대여자($byPatron)와 다릅니다.")
            }
            CheckedOutBook(
                bookId = bookId,
                bookType = bookType,
                byPatron = event.patronId,
            )
        }
    }

    fun handle(event: BookReturnedEvent): Either<InvalidStateError, AvailableBook> {
        return either {
            ensure(event.bookId == bookId) {
                InvalidStateError("반납하려는 도서 ID(${event.bookId})가 현재 도서 ID($bookId)와 다릅니다.")
            }
            ensure(event.bookType == bookType) {
                InvalidStateError("반납하려는 도서 유형(${event.bookType})이 현재 도서 유형($bookType)과 다릅니다.")
            }
            ensure(event.patronId == byPatron) {
                InvalidStateError("반납하려는 도서 대여자(${event.patronId})가 현재 도서 대여자($byPatron)와 다릅니다.")
            }
            AvailableBook(
                bookId = bookId,
                bookType = bookType,
            )
        }
    }

    fun handle(event: BookHoldExpiredEvent): Either<InvalidStateError, AvailableBook> {
        return either {
            ensure(event.bookId == bookId) {
                InvalidStateError("반납하려는 도서 ID(${event.bookId})가 현재 도서 ID($bookId)와 다릅니다.")
            }
            ensure(event.patronId == byPatron) {
                InvalidStateError("반납하려는 도서 예약자(${event.patronId})가 현재 도서 예약자($byPatron)와 다릅니다.")
            }
            AvailableBook(
                bookId = bookId,
                bookType = bookType,
            )
        }
    }
}

internal data class CheckedOutBook(
    override val bookId: BookId,
    override val bookType: BookType,
    val byPatron: PatronId,
) : Book {
    fun handle(event: BookReturnedEvent): Either<InvalidStateError, AvailableBook> {
        return either {
            ensure(event.bookId == bookId) {
                InvalidStateError("반납하려는 도서 ID(${event.bookId})가 현재 도서 ID($bookId)와 다릅니다.")
            }
            ensure(event.bookType == bookType) {
                InvalidStateError("반납하려는 도서 유형(${event.bookType})이 현재 도서 유형($bookType)과 다릅니다.")
            }
            ensure(event.patronId == byPatron) {
                InvalidStateError("반납하려는 도서 대여자(${event.patronId})가 현재 도서 대여자($byPatron)와 다릅니다.")
            }
            AvailableBook(
                bookId = bookId,
                bookType = bookType,
            )
        }
    }
}
