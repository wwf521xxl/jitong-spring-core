package com.jitong.core.report;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter
public class CorrectionStatusConverter implements AttributeConverter<CorrectionStatus, String> {

    @Override
    public String convertToDatabaseColumn(CorrectionStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public CorrectionStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CorrectionStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
