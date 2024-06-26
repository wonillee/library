package io.chrislee.library.lending.book.application.usecase

import arrow.core.Either
import arrow.core.Option
import arrow.core.right
import arrow.core.toOption
import io.chrislee.library.common.domain.SystemError
import io.chrislee.library.lending.book.application.domain.AvailableBook
import io.chrislee.library.lending.book.application.domain.Book
import io.chrislee.library.lending.book.application.domain.BookId
import io.chrislee.library.lending.book.application.domain.BookRepository
import io.chrislee.library.lending.book.application.domain.BookType
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.assertions.arrow.core.shouldBeSome
import io.kotest.core.spec.style.DescribeSpec
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo

class CreateAvailableBookOnInstanceAddedUseCaseImplTest : DescribeSpec({
    val bookRepository = InMemoryBookRepository()
    val impl = CreateAvailableBookUseCaseImpl(bookRepository)
    describe("도서 인스턴스 추가 커맨드를 받았을 때") {
        val command = CreateAvailableBookCommand(
            bookId = BookId.uniqueOne(),
            bookType = BookType.Circulating,
        )
        it("동일한 도서 ID가 존재하는 경우 오류를 발생시켜야 한다") {
            bookRepository.save(AvailableBook(bookId = command.bookId, bookType = command.bookType)).shouldBeRight()
            val error = impl.execute(command).shouldBeLeft()
            expectThat(error) {
                get { this.message }.contains("ID")
            }
        }
        it("동일한 도서 ID가 존재하지 않는 경우 새로운 대여 가능한 도서가 추가되어야 한다") {
            val book = impl.execute(command).shouldBeRight()
            expectThat(book).isA<AvailableBook>().and {
                get { this.bookId }.isEqualTo(command.bookId)
                get { this.bookType }.isEqualTo(command.bookType)
            }
            bookRepository.findByBookId(book.bookId).shouldBeRight().shouldBeSome(book)
        }
    }
    afterAny { bookRepository.clear() }
})

private class InMemoryBookRepository : BookRepository {
    private val books = mutableMapOf<BookId, Book>()

    override suspend fun findByBookId(bookId: BookId): Either<SystemError, Option<Book>> {
        return books[bookId].toOption().right()
    }

    override suspend fun save(book: Book): Either<SystemError, Unit> {
        books[book.bookId] = book
        return Unit.right()
    }

    fun clear() {
        books.clear()
    }
}
