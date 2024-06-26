package io.chrislee.library.lending.patron.adapter.persistence

import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.PatronId
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table

@Table("overdue_checkout")
internal data class OverdueCheckoutEntity(
    @Id val id: Int?,
    val patronId: PatronId,
    val patron: Int,
    val bookId: BookId,
) {
    override fun equals(other: Any?): Boolean {
        return id?.equals(other) ?: false
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}
