package io.chrislee.library.lending

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootApplication
@Import(LendingConfiguration::class)
class LendingApplication

fun main(args: Array<String>) {
    runApplication<LendingApplication>(args = args)
    Thread.currentThread().join()
}
