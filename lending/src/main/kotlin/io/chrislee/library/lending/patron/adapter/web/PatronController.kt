package io.chrislee.library.lending.patron.adapter.web

import arrow.core.Either
import arrow.core.getOrElse
import io.chrislee.library.common.infrastructure.ApiResponse
import io.chrislee.library.lending.patron.application.domain.Patron
import io.chrislee.library.lending.patron.application.domain.PatronCreatedEvent
import io.chrislee.library.lending.patron.application.domain.PatronId
import io.chrislee.library.lending.patron.application.domain.PatronRepository
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
internal class PatronController(private val patronRepository: PatronRepository) {
    @PostMapping(
        "/patrons",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun createPatron(
        @RequestBody request: CreatePatronRequest,
    ): ResponseEntity<ApiResponse<Any>> {
        val patronId = PatronId.from(request.patronId).getOrElse { return ApiResponse.badRequest("고객 ID 입력 오류입니다.") }
        val patronType = Either.catch { Patron.Type.valueOf(request.patronType) }
            .getOrElse { return ApiResponse.badRequest("고객 유형 입력 오류입니다.") }
        val event = PatronCreatedEvent(patronId = patronId, patronType = patronType)
        return patronRepository.save(event).fold(
            ifLeft = { ApiResponse.serverError() },
            ifRight = { ApiResponse.ok() },
        )
    }
}

internal data class CreatePatronRequest(
    val patronId: String,
    val patronType: String,
)
