package com.jitong.core.job;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter
public class JobStatusConverter implements AttributeConverter<JobStatus, String> {

    @Override
    public String convertToDatabaseColumn(JobStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public JobStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : JobStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
