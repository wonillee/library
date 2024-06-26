package io.chrislee.library.lending.patronprofile.adapter.web

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import io.chrislee.library.lending.patronprofile.application.domain.PatronProfile

internal class PatronProfileSerializer : JsonSerializer<PatronProfile>() {
    override fun serialize(value: PatronProfile, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeStartObject()
        gen.writeFieldName("holdsView")
        gen.writeStartArray()
        value.holdsView.source.values.forEach { hold ->
            gen.writeStartObject()
            gen.writeFieldName("bookId")
            gen.writeString(hold.bookId.source)
            gen.writeFieldName("till")
            hold.till.fold({ gen.writeNull() }) { gen.writeString(it.toString()) }
            gen.writeEndObject()
        }
        gen.writeEndArray()
        gen.writeFieldName("checkoutsView")
        gen.writeStartArray()
        value.checkoutsView.source.values.forEach { checkout ->
            gen.writeStartObject()
            gen.writeFieldName("bookId")
            gen.writeString(checkout.bookId.source)
            gen.writeFieldName("till")
            gen.writeString(checkout.till.toString())
            gen.writeEndObject()
        }
        gen.writeEndArray()
        gen.writeEndObject()
    }
}
