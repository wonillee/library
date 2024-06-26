package io.chrislee.library.lending.patron.adapter.persistence

import io.chrislee.library.lending.patron.application.domain.Patron
import io.chrislee.library.lending.patron.application.domain.PatronId
import org.springframework.data.annotation.Id
import org.springframework.data.annotation.PersistenceCreator
import org.springframework.data.annotation.Transient
import org.springframework.data.relational.core.mapping.Table

@Table("patron")
internal data class PatronEntity(
    @Id val id: Int? = null,
    val patronId: PatronId,
    val patronType: Patron.Type,
    @Transient val booksOnHold: Set<HoldEntity>,
    @Transient val overdueCheckouts: Set<OverdueCheckoutEntity>,
) {
    @PersistenceCreator
    constructor(
        id: Int?,
        patronId: PatronId,
        patronType: Patron.Type,
    ) : this(id, patronId, patronType, emptySet(), emptySet())
}
