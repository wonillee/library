package io.chrislee.library.lending.patronprofile.adapter

import arrow.integrations.jackson.module.registerArrowModule
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.MapperFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.chrislee.library.lending.patronprofile.adapter.persistence.DatabasePatronProfileRepository
import io.chrislee.library.lending.patronprofile.adapter.web.PatronProfileSerializer
import io.chrislee.library.lending.patronprofile.application.domain.PatronProfile
import io.chrislee.library.lending.patronprofile.application.domain.PatronProfileRepository
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient

@Configuration
@ComponentScan
@EnableAutoConfiguration
internal class PatronProfileConfiguration {
    @Bean
    fun patronProfileRepository(databaseClient: DatabaseClient): PatronProfileRepository {
        return DatabasePatronProfileRepository(databaseClient)
    }

    @Bean
    fun objectMapper(): ObjectMapper {
        val kotlinModule = KotlinModule.Builder().configure(KotlinFeature.StrictNullChecks, true).build()
        return JsonMapper.builder()
            .addModule(kotlinModule)
            .addModule(JavaTimeModule())
            .addModule(SimpleModule().addSerializer(PatronProfile::class.java, PatronProfileSerializer()))
            .enable(MapperFeature.USE_STD_BEAN_NAMING)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .build()
            .registerArrowModule()
    }
}
