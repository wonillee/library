package io.chrislee.library.lending.patron.adapter.persistence

import io.chrislee.library.lending.patron.application.domain.PatronId
import org.springframework.data.r2dbc.repository.Modifying
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

internal interface R2DBCOverdueCheckoutRepository : CoroutineCrudRepository<OverdueCheckoutEntity, Int> {
    @Modifying
    @Query("DELETE FROM overdue_checkout WHERE patron_id = :patronId")
    suspend fun deleteAllByPatronId(patronId: PatronId): Int
}
