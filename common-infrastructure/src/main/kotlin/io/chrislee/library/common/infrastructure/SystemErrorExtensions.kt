package io.chrislee.library.common.infrastructure

import io.chrislee.library.common.domain.SystemError
import org.springframework.dao.DataIntegrityViolationException

fun transformToSystemError(e: Throwable): SystemError {
    return if (e is DataIntegrityViolationException) {
        SystemError.DataInconsistency("${e.message}")
    } else {
        // TODO 예외 로깅 등의 처리를 공통화할 수도 있음
        SystemError.IOError("${e.message}")
    }
}
