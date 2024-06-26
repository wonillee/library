package io.chrislee.library.common.domain

sealed class SystemError {
    abstract val message: String

    data class DataInconsistency(override val message: String) : SystemError()

    class IOError(override val message: String) : SystemError()
}
