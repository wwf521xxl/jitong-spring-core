package com.jitong.core.group;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.Locale;

@Converter
public class GroupStatusConverter implements AttributeConverter<GroupStatus, String> {

    @Override
    public String convertToDatabaseColumn(GroupStatus attribute) {
        return attribute == null ? null : attribute.name().toLowerCase(Locale.ROOT);
    }

    @Override
    public GroupStatus convertToEntityAttribute(String dbData) {
        return dbData == null ? null : GroupStatus.valueOf(dbData.toUpperCase(Locale.ROOT));
    }
}
