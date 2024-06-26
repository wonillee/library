package io.chrislee.library.catalog

import io.chrislee.library.catalog.adapter.CatalogConfiguration
import org.springframework.boot.SpringBootConfiguration
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Import

@SpringBootConfiguration
@Import(CatalogConfiguration::class)
class CatalogApplication

fun main(args: Array<String>) {
    runApplication<CatalogApplication>(args = args)
    Thread.currentThread().join()
}
