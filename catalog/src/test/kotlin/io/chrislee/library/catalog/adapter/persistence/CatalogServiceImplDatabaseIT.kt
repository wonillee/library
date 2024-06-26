package io.chrislee.library.catalog.adapter.persistence

import io.chrislee.library.catalog.adapter.CatalogConfiguration
import io.chrislee.library.catalog.application.domain.Book
import io.chrislee.library.catalog.application.domain.BookAuthor
import io.chrislee.library.catalog.application.domain.BookId
import io.chrislee.library.catalog.application.domain.BookInstance
import io.chrislee.library.catalog.application.domain.BookTitle
import io.chrislee.library.catalog.application.domain.BookType
import io.chrislee.library.catalog.application.domain.ISBN
import io.chrislee.library.common.infrastructure.TestContainerDatabaseInitializer
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeNone
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.data.r2dbc.DataR2dbcTest
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.r2dbc.core.awaitRowsUpdated
import org.springframework.test.context.ContextConfiguration
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNull

@DataR2dbcTest
@ContextConfiguration(
    classes = [CatalogConfiguration::class],
    initializers = [TestContainerDatabaseInitializer::class],
)
class CatalogServiceImplDatabaseIT {
    @Autowired
    private lateinit var databaseClient: DatabaseClient

    @Autowired
    private lateinit var catalogDatabase: CatalogDatabase

    @AfterEach
    fun afterEach() {
        runTest {
            databaseClient.sql("DELETE FROM book_instance").fetch().awaitRowsUpdated()
            databaseClient.sql("DELETE FROM book").fetch().awaitRowsUpdated()
        }
    }

    @Test
    fun testSaveNewBook() {
        runTest {
            val book = Book(
                isbn = ISBN.from("1234567890").shouldBeRight(),
                title = BookTitle.from("title1").shouldBeRight(),
                author = BookAuthor.from("author1").shouldBeRight(),
            )
            catalogDatabase.findBy(book.isbn).shouldBeRight { it.message }.shouldBeNone()
            catalogDatabase.saveNew(book).shouldBeRight()
            val found = catalogDatabase.findBy(book.isbn).shouldBeRight().shouldBeSome()
            expectThat(found).isEqualTo(book)
            val error = catalogDatabase.saveNew(book).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("중복")
            }
        }
    }

    @Test
    fun testSaveNewBookInstance() {
        runTest {
            val book = Book(
                isbn = ISBN.from("1234567890").shouldBeRight(),
                title = BookTitle.from("title1").shouldBeRight(),
                author = BookAuthor.from("author1").shouldBeRight(),
            )
            catalogDatabase.saveNew(book).shouldBeRight()
            val bookId = BookId.uniqueOne()
            val maybeRow = databaseClient.sql("SELECT * FROM book_instance WHERE book_id = :bookId")
                .bind("bookId", bookId.source)
                .fetch().awaitOneOrNull()
            expectThat(maybeRow).isNull()
            val bookInstance = BookInstance(
                bookIsbn = book.isbn,
                bookId = bookId,
                bookType = BookType.Restricted,
            )
            catalogDatabase.saveNew(bookInstance).shouldBeRight()
            val row = databaseClient.sql("SELECT * FROM book_instance WHERE book_id = :bookId")
                .bind("bookId", bookInstance.bookId.source)
                .fetch().one().block()
            expectThat(row) {
                get { this?.get("book_id") }.isEqualTo(bookInstance.bookId.source)
                get { this?.get("isbn") }.isEqualTo(bookInstance.bookIsbn.source)
            }
        }
    }
}
