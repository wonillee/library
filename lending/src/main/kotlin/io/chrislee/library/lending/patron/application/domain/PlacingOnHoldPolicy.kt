package io.chrislee.library.lending.patron.application.domain

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.BookType
import io.chrislee.library.lending.patron.application.domain.PlacingOnHoldPolicy.Allowance
import io.chrislee.library.lending.patron.application.domain.PlacingOnHoldPolicy.Rejection

internal data class PlacingOnHoldContext(
    val availableBook: AvailableBook,
    val patron: Patron,
    val holdDuration: HoldDuration,
)

internal interface PlacingOnHoldPoliciesProvider {
    fun provide(): List<PlacingOnHoldPolicy>
}

internal class DefaultPlacingOnHoldPoliciesProvider : PlacingOnHoldPoliciesProvider {
    override fun provide(): List<PlacingOnHoldPolicy> {
        return listOf(
            OnlyResearcherPatronCanHoldRestrictedBooksPolicy,
            OverdueCheckoutsRejectionPolicy,
            RegularPatronMaximumNumberOfHoldsPolicy,
            OnlyResearcherPatronCanPlaceOpenEndedHoldsPolicy,
        )
    }
}

@FunctionalInterface
internal sealed interface PlacingOnHoldPolicy {
    fun apply(context: PlacingOnHoldContext): Either<Rejection, Allowance>

    data object Allowance

    data class Rejection(val reason: String)
}

internal data object OnlyResearcherPatronCanHoldRestrictedBooksPolicy : PlacingOnHoldPolicy {
    override fun apply(context: PlacingOnHoldContext): Either<Rejection, Allowance> {
        if (context.availableBook.bookType == BookType.Restricted && context.patron.patronType == Patron.Type.Regular) {
            return Rejection("정규 도서 대여자는 제한된 책을 대여 예약할 수 없습니다.").left()
        }
        return Allowance.right()
    }
}

internal data object OverdueCheckoutsRejectionPolicy : PlacingOnHoldPolicy {
    override fun apply(context: PlacingOnHoldContext): Either<Rejection, Allowance> {
        val patron = context.patron
        if (patron.patronType == Patron.Type.Regular && patron.countsOfOverdueCheckouts() >= 2) {
            return Rejection("정규 도서 대여자는 반납 기간을 초과한 대여 도서가 2권 이상 있는 경우 도서 대여 예약을 할 수 없습니다.").left()
        }
        return Allowance.right()
    }
}

internal data object RegularPatronMaximumNumberOfHoldsPolicy : PlacingOnHoldPolicy {
    override fun apply(context: PlacingOnHoldContext): Either<Rejection, Allowance> {
        if (context.patron.patronType == Patron.Type.Regular &&
            context.patron.numberOfHolds() >= PatronHolds.MAX_NUMBER_OF_HOLDS
        ) {
            return Rejection("정규 도서 대여자는 최대 4개의 책을 대여 예약할 수 있습니다.").left()
        }
        return Allowance.right()
    }
}

internal data object OnlyResearcherPatronCanPlaceOpenEndedHoldsPolicy : PlacingOnHoldPolicy {
    override fun apply(context: PlacingOnHoldContext): Either<Rejection, Allowance> {
        if (context.holdDuration is HoldDuration.OpenEnded && context.patron.patronType == Patron.Type.Regular) {
            return Rejection("정규 도서 대여자는 기간 무제한 예약을 할 수 없습니다.").left()
        }
        return Allowance.right()
    }
}
