package io.void.core.serialization

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule

/** The single JSON configuration for public core contracts. */
object VoidObjectMapper {
 val instance: ObjectMapper = jacksonObjectMapper()
  .registerKotlinModule()
  .registerModule(JavaTimeModule())
  .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
  .setSerializationInclusion(JsonInclude.Include.NON_NULL)
  .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
  .enable(SerializationFeature.WRITE_ENUMS_USING_TO_STRING)
  .enable(DeserializationFeature.READ_ENUMS_USING_TO_STRING)
  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
}
