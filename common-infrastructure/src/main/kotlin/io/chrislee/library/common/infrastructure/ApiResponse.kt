package io.chrislee.library.common.infrastructure

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity

class ApiResponse<T> private constructor(
    @get:JsonProperty val status: Int,
    @get:JsonProperty @JsonInclude(JsonInclude.Include.NON_NULL) val body: T? = null,
    @get:JsonProperty @JsonInclude(JsonInclude.Include.NON_NULL) val errorMessage: String? = null,
) {
    companion object {
        private val logger = mu.KotlinLogging.logger { }

        fun <T> ok(): ResponseEntity<ApiResponse<T>> {
            return ResponseEntity.ok(ApiResponse(status = 200))
        }

        fun <T> ok(body: T): ResponseEntity<ApiResponse<T>> {
            return ResponseEntity.ok(ApiResponse(status = 200, body = body))
        }

        fun <T> badRequest(errorMessage: String): ResponseEntity<ApiResponse<T>> {
            return ResponseEntity.status(400).body(ApiResponse(status = 400, errorMessage = errorMessage))
        }

        fun <T> notFound(errorMessage: String): ResponseEntity<ApiResponse<T>> {
            return ResponseEntity.status(404).body(ApiResponse(status = 404, errorMessage = errorMessage))
        }

        fun <T> serverError(): ResponseEntity<ApiResponse<T>> {
            return ResponseEntity.status(500).body(ApiResponse(status = 500, errorMessage = "시스템 오류가 발생했습니다."))
        }

        fun <T> serverError(errorMessage: String): ResponseEntity<ApiResponse<T>> {
            logger.error { errorMessage }
            return serverError()
        }
    }
}
