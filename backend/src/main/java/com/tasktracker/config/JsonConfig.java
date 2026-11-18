package com.tasktracker.config;

import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JsonConfig {
  @Bean
  Jackson2ObjectMapperBuilderCustomizer strictStrings() {
    // Jackson's general scalar flag does not prohibit number-to-String conversion.
    return builder ->
        builder.postConfigurer(
            mapper -> {
              var strings = mapper.coercionConfigFor(LogicalType.Textual);
              for (var shape :
                  new CoercionInputShape[] {
                    CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean
                  }) {
                strings.setCoercion(shape, CoercionAction.Fail);
              }
            });
  }
}
