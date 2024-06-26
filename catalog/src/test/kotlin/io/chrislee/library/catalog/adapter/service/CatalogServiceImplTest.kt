package io.chrislee.library.catalog.adapter.service

import arrow.core.None
import arrow.core.left
import arrow.core.right
import arrow.core.some
import io.chrislee.library.catalog.adapter.persistence.CatalogDatabase
import io.chrislee.library.catalog.application.domain.Book
import io.chrislee.library.catalog.application.domain.BookAuthor
import io.chrislee.library.catalog.application.domain.BookInstance
import io.chrislee.library.catalog.application.domain.BookTitle
import io.chrislee.library.catalog.application.domain.BookType
import io.chrislee.library.catalog.application.domain.CatalogError
import io.chrislee.library.catalog.application.domain.ISBN
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.common.infrastructure.mockTransactionalOperator
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.kafka.core.KafkaTemplate
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.util.concurrent.CompletableFuture

class CatalogServiceImplTest : DescribeSpec({
    val catalogDatabase = mockk<CatalogDatabase>()
    val transactionalOperator = mockTransactionalOperator().first
    val kafkaTemplate = mockk<KafkaTemplate<String, ByteArray>>()
    val book = Book(
        isbn = ISBN.from("0321125215").shouldBeRight(),
        title = BookTitle.from("Domain-Driven Design").shouldBeRight(),
        author = BookAuthor.from("Eric Evans").shouldBeRight(),
    )
    val catalogService = CatalogServiceImpl(catalogDatabase, transactionalOperator, kafkaTemplate, "topic")

    fun databaseWorks() {
        coEvery { catalogDatabase.saveNew(book) } returns book.right()
        coEvery { catalogDatabase.saveNew(any<BookInstance>()) } coAnswers { (args[0] as BookInstance).right() }
    }

    fun databaseDoesNotWork() {
        coEvery { catalogDatabase.saveNew(any<Book>()) } returns SystemError.IOError("io error").left()
        coEvery { catalogDatabase.saveNew(any<BookInstance>()) } returns SystemError.IOError("io error").left()
    }

    fun kafkaWorks() {
        every { kafkaTemplate.send(any(), any()) } returns CompletableFuture.completedFuture(mockk())
    }

    fun kafkaDoesNotWork() {
        every { kafkaTemplate.send(any(), any()) } returns CompletableFuture.failedFuture(RuntimeException())
    }

    fun thereIsBookWith(isbn: ISBN) {
        coEvery { catalogDatabase.findBy(isbn) } returns book.some().right()
    }

    fun thereIsNoBookWith(isbn: ISBN) {
        coEvery { catalogDatabase.findBy(isbn) } returns None.right()
    }

    it("새로운 도서를 카탈로그에 추가할 수 있어야 한다") {
        databaseWorks()
        kafkaWorks()
        catalogService.addBook(book.author.source, book.title.source, book.isbn.source).shouldBeRight(book)
        coVerify(exactly = 1) { catalogDatabase.saveNew(any<Book>()) }
    }

    it("새로운 도서 인스턴스를 카탈로그에 추가할 수 있어야 한다") {
        databaseWorks()
        kafkaWorks()
        thereIsBookWith(book.isbn)
        val instance = catalogService.addBookInstance(book.isbn.source, BookType.Restricted).shouldBeRight()
        expectThat(instance) {
            get { this.bookIsbn }.isEqualTo(book.isbn)
            get { this.bookType }.isEqualTo(BookType.Restricted)
        }
        coVerify(exactly = 1) { catalogDatabase.saveNew(any<BookInstance>()) }
        verify(exactly = 1) { kafkaTemplate.send(any(), any()) }
    }

    it("ISBN이 존재하지 않는 도서 인스턴스는 카탈로그에 추가될 수 없다") {
        databaseWorks()
        kafkaWorks()
        thereIsNoBookWith(book.isbn)
        val error = catalogService.addBookInstance(book.isbn.source, BookType.Restricted).shouldBeLeft()
        expectThat(error).isA<CatalogError.BookNotExist>()
        coVerify(exactly = 0) { catalogDatabase.saveNew(any<BookInstance>()) }
        verify(exactly = 0) { kafkaTemplate.send(any(), any()) }
    }

    it("데이터베이스 예외가 발생할 경우 도서 추가가 실패해야 한다") {
        databaseDoesNotWork()
        kafkaWorks()
        val error = catalogService.addBook(book.author.source, book.title.source, book.isbn.source).shouldBeLeft()
        expectThat(error).isA<CatalogError.System>()
        coVerify(exactly = 1) { catalogDatabase.saveNew(any<Book>()) }
    }

    it("데이터베이스 예외가 발생할 경우 도서 인스턴스 추가가 실패해야 한다") {
        databaseDoesNotWork()
        kafkaWorks()
        thereIsBookWith(book.isbn)
        val error = catalogService.addBookInstance(book.isbn.source, BookType.Restricted).shouldBeLeft()
        expectThat(error).isA<CatalogError.System>()
        coVerify(exactly = 1) { catalogDatabase.saveNew(any<BookInstance>()) }
        verify(exactly = 0) { kafkaTemplate.send(any(), any()) }
    }

    it("카프카 예외가 발생할 경우 도서 인스턴스 추가가 실패해야 한다") {
        databaseWorks()
        kafkaDoesNotWork()
        thereIsBookWith(book.isbn)
        val error = catalogService.addBookInstance(book.isbn.source, BookType.Restricted).shouldBeLeft()
        expectThat(error).isA<CatalogError.System>()
        coVerify(exactly = 1) { catalogDatabase.saveNew(any<BookInstance>()) }
        verify(exactly = 1) { kafkaTemplate.send(any(), any()) }
    }

    afterTest { clearMocks(catalogDatabase, kafkaTemplate) }
})
