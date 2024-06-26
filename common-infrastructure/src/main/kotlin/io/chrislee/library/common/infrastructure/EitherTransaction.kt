package io.chrislee.library.common.infrastructure

import arrow.core.Either
import arrow.core.left
import arrow.core.raise.Raise
import arrow.core.raise.fold
import arrow.core.right
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.transaction.reactive.executeAndAwait
import kotlin.experimental.ExperimentalTypeInference

@OptIn(ExperimentalTypeInference::class)
suspend inline fun <reified E, reified A> transactionalEither(
    transactionalOperator: TransactionalOperator,
    @BuilderInference noinline block: suspend Raise<E>.() -> A,
): Either<E, A> =
    transactionalOperator.executeAndAwait { tx ->
        // catch를 설정하지 않은 이유:
        // Either를 반환하는 모든 함수는 예외를 Left로 처리했다는 가정이 있다.
        // 따라서, 놓치는 예외가 있다면 애플리케이션 최상단에서 잡도록 한다.
        fold(
            block = {
                block.invoke(this)
            },
            recover = {
                tx.setRollbackOnly()
                it.left()
            },
            transform = {
                it.right()
            },
        )
    }
