package io.chrislee.library.lending.book.adapter.persistence

import io.chrislee.library.lending.book.application.domain.BookId
import org.springframework.data.repository.kotlin.CoroutineCrudRepository
import org.springframework.stereotype.Repository

@Repository
internal interface R2DBCBookRepository : CoroutineCrudRepository<BookEntity, Int> {
    suspend fun findByBookId(bookId: BookId): BookEntity?
}
