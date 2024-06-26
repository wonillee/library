package io.chrislee.library.catalog.adapter.web

import io.chrislee.library.catalog.application.domain.Book
import io.chrislee.library.catalog.application.domain.BookInstance
import io.chrislee.library.catalog.application.domain.BookType
import io.chrislee.library.catalog.application.domain.CatalogError
import io.chrislee.library.catalog.application.domain.CatalogService
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.common.infrastructure.ApiResponse
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/catalog")
internal class CatalogController(private val catalogService: CatalogService) {
    @PostMapping(
        "/books",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun addBook(
        @RequestBody request: AddBookRequest,
    ): ResponseEntity<ApiResponse<Book>> {
        return catalogService.addBook(request.author, request.title, request.isbn).fold(
            ifLeft = {
                when (it) {
                    is CatalogError.BookNotExist -> ApiResponse.notFound("도서가 존재하지 않습니다")
                    is CatalogError.Input -> ApiResponse.badRequest(it.message)
                    is CatalogError.System -> {
                        when (it.systemError) {
                            is SystemError.DataInconsistency -> ApiResponse.badRequest("ISBN이 중복됩니다")
                            is SystemError.IOError -> ApiResponse.serverError(it.systemError.message)
                        }
                    }
                }
            },
            ifRight = { ApiResponse.ok(it) },
        )
    }

    @PostMapping(
        "/bookInstances",
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    suspend fun addBookInstance(
        @RequestBody request: AddBookInstanceRequest,
    ): ResponseEntity<ApiResponse<BookInstance>> {
        return catalogService.addBookInstance(request.isbn, request.bookType).fold(
            ifLeft = {
                when (it) {
                    is CatalogError.BookNotExist -> ApiResponse.notFound("도서가 존재하지 않습니다")
                    is CatalogError.Input -> ApiResponse.badRequest("입력 정보가 올바르지 않습니다")
                    is CatalogError.System -> ApiResponse.serverError(it.systemError.message)
                }
            },
            ifRight = { ApiResponse.ok(it) },
        )
    }

    @GetMapping("/books", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun findAllBooks(): ResponseEntity<ApiResponse<List<Book>>> {
        return catalogService.findAllBooks().fold(
            ifLeft = { ApiResponse.serverError(it.systemError.message) },
            ifRight = { ApiResponse.ok(it) },
        )
    }

    @GetMapping("/bookInstances", produces = [MediaType.APPLICATION_JSON_VALUE])
    suspend fun findAllBookInstances(): ResponseEntity<ApiResponse<List<BookInstance>>> {
        return catalogService.findAllBookInstances().fold(
            ifLeft = { ApiResponse.serverError(it.systemError.message) },
            ifRight = { ApiResponse.ok(it) },
        )
    }
}

internal data class AddBookRequest(
    val author: String,
    val title: String,
    val isbn: String,
)

internal data class AddBookInstanceRequest(
    val isbn: String,
    val bookType: BookType,
)
