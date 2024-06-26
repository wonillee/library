package io.chrislee.library.common.infrastructure

import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.MountableFile

class TestContainerDatabaseInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val initSqlFile = MountableFile.forClasspathResource("init.sql")
        // https://hub.docker.com/_/postgres 의 Initialization scripts 항목 참고
        val dockerInitSqlFile = "/docker-entrypoint-initdb.d/init.sql"
        val container = PostgreSQLContainer("postgres:16").withCopyFileToContainer(initSqlFile, dockerInitSqlFile)
        container.start()
        applicationContext.addApplicationListener { e ->
            if (e is ContextClosedEvent) {
                container.stop()
            }
        }
        TestPropertyValues.of(
            mapOf(
                "spring.r2dbc.url" to "r2dbc:postgresql://${container.host}:${container.firstMappedPort}/${container.databaseName}",
                "spring.r2dbc.username" to container.username,
                "spring.r2dbc.password" to container.password,
            ),
        ).applyTo(applicationContext)
    }
}
