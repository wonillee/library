package io.chrislee.library.lending.patron.adapter.persistence

import io.chrislee.library.lending.patron.application.domain.PatronId
import org.springframework.data.repository.kotlin.CoroutineCrudRepository

internal interface R2DBCPatronRepository : CoroutineCrudRepository<PatronEntity, Int> {
    suspend fun findByPatronId(patronId: PatronId): PatronEntity?
}
