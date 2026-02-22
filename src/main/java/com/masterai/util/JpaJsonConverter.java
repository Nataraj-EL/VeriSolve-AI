package com.masterai.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Converter
@Component
public class JpaJsonConverter implements AttributeConverter<Object, String> {

    private static final Logger logger = LoggerFactory.getLogger(JpaJsonConverter.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Object attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            logger.error("Error converting object to JSON string", e);
            return null;
        }
    }

    @Override
    public Object convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            // This is a generic converter, effectively deserializing to default types (Map, List, etc.)
            // For specific types, we might need specific converters or handle it at the entity level
            return objectMapper.readValue(dbData, Object.class);
        } catch (IOException e) {
            logger.error("Error converting JSON string to object", e);
            return null;
        }
    }
}
