package com.tunegocio.turnosapi.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Convierte List&lt;String&gt; ↔ TEXT (valores separados por coma) para JPA.
 */
@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

    private static final String SEPARATOR = ",";

    @Override
    public String convertToDatabaseColumn(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(SEPARATOR, list);
    }

    @Override
    public List<String> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(dbData.split(SEPARATOR)));
    }
}
