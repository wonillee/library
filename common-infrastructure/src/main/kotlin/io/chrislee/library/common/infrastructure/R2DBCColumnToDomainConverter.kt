package io.chrislee.library.common.infrastructure

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.toOption
import io.chrislee.library.common.domain.SystemError

object R2DBCColumnToDomainConverter {
    inline fun <reified C, reified D, reified E> Map<String, Any>.convert(
        columnName: String,
        transform: (C) -> Either<E, D>,
        mapLeft: (E?) -> SystemError.DataInconsistency,
    ): Either<SystemError.DataInconsistency, D> {
        return either {
            val cell = this@convert[columnName] as? C
            cell.toOption()
                .map { transform(it).mapLeft(mapLeft).bind() }
                .getOrElse { raise(mapLeft(null)) }
        }
    }
}
