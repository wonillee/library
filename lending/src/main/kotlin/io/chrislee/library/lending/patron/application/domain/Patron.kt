package io.chrislee.library.lending.patron.application.domain

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.some
import arrow.core.toOption
import io.chrislee.library.common.domain.DomainEventId
import io.chrislee.library.common.domain.InvalidStateError
import io.chrislee.library.common.domain.NewType
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookOnHold
import io.chrislee.library.lending.book.application.domain.CheckedOutBook
import io.chrislee.library.lending.patron.application.domain.Hold.Companion.asHold
import java.time.Duration
import java.time.Instant
import java.util.UUID

@JvmInline
internal value class PatronId private constructor(override val source: String) : NewType<String> {
    companion object {
        fun uniqueOne(): PatronId = PatronId(UUID.randomUUID().toString())

        fun from(id: String): Either<InvalidStateError, PatronId> {
            return either {
                ensure(id.trim().isNotBlank()) { InvalidStateError("고객 ID가 비어 있습니다") }
                PatronId(id.trim())
            }
        }
    }
}

@JvmInline
internal value class Hold(override val source: BookId) : NewType<BookId> {
    companion object {
        fun BookId.asHold(): Hold = Hold(this)
    }
}

@JvmInline
internal value class PatronHolds(override val source: Set<Hold>) : NewType<Set<Hold>> {
    companion object {
        const val MAX_NUMBER_OF_HOLDS: Int = 4

        fun empty(): PatronHolds {
            return PatronHolds(emptySet())
        }
    }

    fun asSequnce(): Sequence<Hold> {
        return source.asSequence()
    }

    fun has(bookOnHold: BookOnHold): Boolean {
        return source.contains(bookOnHold.bookId.asHold())
    }

    fun has(bookId: BookId): Boolean {
        return source.contains(bookId.asHold())
    }

    fun count(): Int = source.size

    fun maximumHoldsAfterHolding(): Boolean {
        return count() + 1 == MAX_NUMBER_OF_HOLDS
    }

    fun add(hold: Hold): PatronHolds {
        return PatronHolds(source + hold)
    }

    fun remove(hold: Hold): PatronHolds {
        return PatronHolds(source - hold)
    }
}

@JvmInline
internal value class NumberOfDays private constructor(override val source: Int) : NewType<Int> {
    companion object {
        fun of(days: Int): Either<InvalidStateError, NumberOfDays> {
            return either {
                ensure(days > 0) { InvalidStateError("일은 양의 정수여야 합니다: $days") }
                NumberOfDays(days)
            }
        }
    }

    fun isGreaterThan(days: Int): Boolean {
        return source > days
    }
}

internal interface HoldDuration {
    fun holdTill(): Option<Instant>

    data class OpenEnded(val from: Instant) : HoldDuration {
        override fun holdTill(): Option<Instant> {
            return None
        }
    }

    class CloseEnded private constructor(val from: Instant, private val to: Instant) : HoldDuration {
        companion object {
            fun of(
                from: Instant,
                days: NumberOfDays,
            ): CloseEnded {
                return CloseEnded(from, from.plus(Duration.ofDays(days.source.toLong())))
            }
        }

        override fun holdTill(): Option<Instant> {
            return to.some()
        }
    }
}

internal class CheckoutDuration private constructor(val from: Instant, private val days: NumberOfDays) {
    companion object {
        private const val MAX_CHECKOUT_DURATION: Int = 60

        fun of(
            from: Instant,
            days: NumberOfDays,
        ): Either<InvalidStateError, CheckoutDuration> {
            return either {
                ensure(!days.isGreaterThan(MAX_CHECKOUT_DURATION)) {
                    InvalidStateError("최대 ${MAX_CHECKOUT_DURATION}일까지 체크아웃할 수 있습니다.")
                }
                CheckoutDuration(from, days)
            }
        }

        fun maxDuration(from: Instant): CheckoutDuration {
            val maximumNumberOfDays = NumberOfDays.of(MAX_CHECKOUT_DURATION).getOrNull()!!
            return CheckoutDuration(from, maximumNumberOfDays)
        }
    }

    fun till(): Instant {
        return from.plus(Duration.ofDays(days.source.toLong()))
    }
}

@JvmInline
internal value class OverdueCheckouts(override val source: Set<BookId>) : NewType<Set<BookId>> {
    fun add(bookId: BookId): OverdueCheckouts {
        return OverdueCheckouts(source + bookId)
    }

    fun remove(bookId: BookId): OverdueCheckouts {
        return OverdueCheckouts(source - bookId)
    }

    fun has(bookId: BookId): Boolean {
        return source.contains(bookId)
    }

    fun count(): Int {
        return source.size
    }

    fun asSequence(): Sequence<BookId> {
        return source.asSequence()
    }

    companion object {
        fun empty(): OverdueCheckouts {
            return OverdueCheckouts(emptySet())
        }
    }
}

internal data class Patron(
    val patronId: PatronId,
    val patronType: Type,
    val placingOnHoldPolicies: List<PlacingOnHoldPolicy>,
    val patronHolds: PatronHolds,
    val overdueCheckouts: OverdueCheckouts,
) {
    enum class Type {
        Regular,
        Researcher,
    }

    fun placeOnHold(
        book: AvailableBook,
        holdDuration: HoldDuration,
    ): Either<BookHoldFailedEvent, BookPlacedOnHoldEvents> {
        return either {
            val maybeRejection = this@Patron.canHold(book, holdDuration)
            ensure(maybeRejection.isNone()) {
                BookHoldFailedEvent(
                    patronId = patronId,
                    bookId = book.bookId,
                    reason = maybeRejection.map { it.reason }.getOrElse { "" },
                )
            }
            val bookPlacedOnHoldEvent = BookPlacedOnHoldEvent(
                patronId = patronId,
                bookId = book.bookId,
                bookType = book.bookType,
                holdDuration = holdDuration,
            )
            BookPlacedOnHoldEvents(
                patronId = patronId,
                bookPlacedOnHoldEvent = bookPlacedOnHoldEvent,
                maximumNumberOnHoldsReachedEvent = if (patronHolds.maximumHoldsAfterHolding()) {
                    MaximumNumberOfHoldsReachedEvent(
                        patronId = patronId,
                    ).some()
                } else {
                    None
                },
            )
        }
    }

    fun cancelHold(book: BookOnHold): Either<BookHoldCancellingFailedEvent, BookHoldCancelledEvent> {
        return either {
            ensure(patronHolds.has(book)) {
                BookHoldCancellingFailedEvent(
                    id = DomainEventId.uniqueOne(),
                    patronId = patronId,
                    bookId = book.bookId,
                    reason = BookHoldCancellingFailedEvent.Reason.BookNotHeld,
                    details = "고객이 해당 도서를 대여 예약하지 않은 상태입니다",
                )
            }
            BookHoldCancelledEvent(
                id = DomainEventId.uniqueOne(),
                patronId = patronId,
                bookId = book.bookId,
            )
        }
    }

    fun checkOut(
        bookOnHold: BookOnHold,
        checkoutDuration: CheckoutDuration,
    ): Either<BookCheckingOutFailedEvent, BookCheckedOutEvent> {
        return either {
            ensure(patronHolds.has(bookOnHold)) {
                BookCheckingOutFailedEvent(
                    patronId = patronId,
                    bookId = bookOnHold.bookId,
                    reason = "고객이 대여 예약하지 않은 도서를 대여 시도하고 있습니다",
                )
            }
            BookCheckedOutEvent(
                patronId = patronId,
                bookId = bookOnHold.bookId,
                bookType = bookOnHold.bookType,
                till = checkoutDuration.till(),
            )
        }
    }

    fun returnBook(checkedOutBook: CheckedOutBook): Either<InvalidStateError, BookReturnedEvent> {
        return either {
            BookReturnedEvent(
                patronId = patronId,
                bookId = checkedOutBook.bookId,
                bookType = checkedOutBook.bookType,
            )
        }
    }

    fun countsOfOverdueCheckouts(): Int {
        return overdueCheckouts.source.size
    }

    fun numberOfHolds(): Int {
        return patronHolds.count()
    }

    private fun canHold(
        availableBook: AvailableBook,
        holdDuration: HoldDuration,
    ): Option<PlacingOnHoldPolicy.Rejection> {
        val context = PlacingOnHoldContext(
            availableBook = availableBook,
            patron = this,
            holdDuration = holdDuration,
        )
        return placingOnHoldPolicies
            .map { policy -> policy.apply(context) }
            .find { it.isLeft() }
            .toOption()
            .map { (it as Either.Left).value }
    }
}
