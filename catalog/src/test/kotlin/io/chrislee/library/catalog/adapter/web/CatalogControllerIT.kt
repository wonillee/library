package io.chrislee.library.catalog.adapter.web

import arrow.core.raise.either
import arrow.core.right
import com.ninjasquad.springmockk.MockkBean
import io.chrislee.library.catalog.adapter.CatalogConfiguration
import io.chrislee.library.catalog.application.domain.Book
import io.chrislee.library.catalog.application.domain.BookAuthor
import io.chrislee.library.catalog.application.domain.BookId
import io.chrislee.library.catalog.application.domain.BookInstance
import io.chrislee.library.catalog.application.domain.BookTitle
import io.chrislee.library.catalog.application.domain.BookType
import io.chrislee.library.catalog.application.domain.CatalogError
import io.chrislee.library.catalog.application.domain.CatalogService
import io.chrislee.library.catalog.application.domain.ISBN
import io.chrislee.library.common.domain.SystemError
import io.mockk.clearMocks
import io.mockk.coEvery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.test.context.ContextConfiguration
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.reactive.function.BodyInserters
import reactor.core.publisher.Mono

@WebFluxTest(CatalogConfiguration::class)
@AutoConfigureWebTestClient
@ContextConfiguration(
    classes = [
        CatalogController::class,
    ],
)
class CatalogControllerIT {
    @Autowired
    private lateinit var client: WebTestClient

    @MockkBean
    private lateinit var catalogService: CatalogService

    private val books = mutableMapOf<String, Book>()
    private val bookInstances = mutableMapOf<BookId, BookInstance>()

    @BeforeEach
    fun beforeEach() {
        coEvery { catalogService.addBook(any(), any(), any()) } answers {
            either {
                val author = BookAuthor.from(firstArg()).mapLeft { CatalogError.Input("author") }.bind()
                val title = BookTitle.from(secondArg()).mapLeft { CatalogError.Input("title") }.bind()
                val isbn = ISBN.from(thirdArg()).mapLeft { CatalogError.Input("isbn") }.bind()
                val book = Book(isbn, title, author)
                if (books.contains(thirdArg())) raise(CatalogError.System(SystemError.DataInconsistency("duplicate")))
                books[thirdArg()] = book
                book
            }
        }
        coEvery { catalogService.addBookInstance(any(), any()) } answers {
            either {
                val isbn = ISBN.from(firstArg()).mapLeft { CatalogError.Input("isbn") }.bind()
                if (!books.contains(firstArg())) raise(CatalogError.BookNotExist(isbn))
                val bookType: BookType = secondArg()
                val bookId = BookId.uniqueOne()
                val instance = BookInstance(isbn, bookId, bookType)
                bookInstances[instance.bookId] = instance
                instance
            }
        }
        coEvery { catalogService.findAllBooks() } answers { books.values.toList().right() }
        coEvery { catalogService.findAllBookInstances() } answers { bookInstances.values.toList().right() }
    }

    @AfterEach
    fun afterEach() {
        clearMocks(catalogService)
        books.clear()
        bookInstances.clear()
    }

    @DisplayName("도서 정보 입력이 잘못된 경우 bad request가 발생해야 한다")
    @ParameterizedTest
    @ValueSource(ints = [0, 1, 2])
    fun shouldNotAddBookWithBadRequest(omit: Int) {
        runTest {
            val params = mutableListOf("author", "title", "0123456789")
            params[omit] = ""
            val request = AddBookRequest(params[0], params[1], params[2])
            client.post()
                .uri("/catalog/books")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), AddBookRequest::class.java)
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @DisplayName("같은 도서(동일한 ISBN)는 단 한번 추가할 수 있다")
    @Test
    fun shouldAddSameBookOnlyOnce() {
        runTest {
            val request = AddBookRequest("author", "title", "0123456789")
            client.post()
                .uri("/catalog/books")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), AddBookRequest::class.java)
                .exchange()
                .expectStatus().isOk
            client.post()
                .uri("/catalog/books")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), AddBookRequest::class.java)
                .exchange()
                .expectStatus().isBadRequest
            client.get()
                .uri("/catalog/books")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .jsonPath("$.body.[0].author").isEqualTo(request.author)
                .jsonPath("$.body.[0].title").isEqualTo(request.title)
                .jsonPath("$.body.[0].isbn").isEqualTo(request.isbn)
                .jsonPath("$.body.[1]").doesNotExist()
        }
    }

    @DisplayName("도서 인스턴스 정보 입력이 잘못된 경우 bad request가 발생되어야 한다")
    @ParameterizedTest
    @ValueSource(ints = [0, 1])
    fun shouldNotAddInstanceWithBadRequest(omit: Int) {
        runTest {
            val params = mutableListOf("0123456789", "Restricted")
            params[omit] = ""
            val request = """
                {
                    "isbn": "${params[0]}",
                    "bookType": "${params[1]}"
                }
            """.trimIndent()
            client.post()
                .uri("/catalog/bookInstances")
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(request))
                .exchange()
                .expectStatus().isBadRequest
        }
    }

    @DisplayName("존재하지 않는 도서에 대한 인스턴스는 생성될 수 없다")
    @Test
    fun shouldNotAddInstanceWithNonExistentBook() {
        runTest {
            val request = AddBookInstanceRequest(
                isbn = "0123456789",
                bookType = BookType.Restricted,
            )
            client.post()
                .uri("/catalog/bookInstances")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(request), AddBookInstanceRequest::class.java)
                .exchange()
                .expectStatus().isNotFound
                .expectBody().jsonPath("$.errorMessage").isEqualTo("도서가 존재하지 않습니다")
        }
    }

    @DisplayName("존재하는 도서에 대한 인스턴스를 계속 생성할 수 있다")
    @Test
    fun shouldAddInstancesWithSameBookManyTimes() {
        runTest {
            val addBookRequest = AddBookRequest(
                author = "author",
                title = "title",
                isbn = "0123456789",
            )
            client.post()
                .uri("/catalog/books")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(addBookRequest), AddBookRequest::class.java)
                .exchange()
                .expectStatus().isOk
            val addBookInstanceRequest = AddBookInstanceRequest(
                isbn = addBookRequest.isbn,
                bookType = BookType.Restricted,
            )
            repeat(10) {
                client.post()
                    .uri("/catalog/bookInstances")
                    .body(Mono.just(addBookInstanceRequest), AddBookInstanceRequest::class.java)
                    .exchange()
                    .expectStatus().isOk
            }
            client.get()
                .uri("/catalog/bookInstances")
                .exchange()
                .expectStatus().isOk
                .expectBody()
                .let { body ->
                    repeat(10) {
                        body.jsonPath("$.body.[$it].bookIsbn").isEqualTo(addBookInstanceRequest.isbn)
                        body.jsonPath("$.body.[$it].bookType").isEqualTo(addBookInstanceRequest.bookType.name)
                    }
                    body
                }
                .jsonPath("$.body.[10]").doesNotExist()
        }
    }
}
