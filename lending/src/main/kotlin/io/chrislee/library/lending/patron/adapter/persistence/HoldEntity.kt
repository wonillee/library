package io.chrislee.library.lending.patron.adapter.persistence

import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.patron.application.domain.PatronId
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("hold")
internal data class HoldEntity(
    @Id val id: Int?,
    val patronId: PatronId,
    val patron: Int,
    val bookId: BookId,
    val till: Instant?,
) {
    override fun equals(other: Any?): Boolean {
        return id?.equals(other) ?: false
    }

    override fun hashCode(): Int {
        return id?.hashCode() ?: 0
    }
}
