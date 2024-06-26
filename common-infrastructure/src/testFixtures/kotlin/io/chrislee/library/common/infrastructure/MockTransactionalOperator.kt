package io.chrislee.library.common.infrastructure

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.reactor.mono
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator

fun mockTransactionalOperator(): Pair<TransactionalOperator, ReactiveTransactionManager> {
    val reactiveTransactionManager = mockk<ReactiveTransactionManager>()
    val tx = mockk<ReactiveTransaction>()
    every { tx.setRollbackOnly() } returns Unit
    every { reactiveTransactionManager.getReactiveTransaction(any()) } answers { mono { tx } }
    every { reactiveTransactionManager.commit(any()) } answers { mono { mockk() } }
    every { reactiveTransactionManager.rollback(any()) } answers { mono { mockk() } }
    return Pair(TransactionalOperator.create(reactiveTransactionManager), reactiveTransactionManager)
}
