package com.jitong.core.report;

import java.util.Locale;

public enum RecordStatus {
    VALID,
    PENDING,
    VOID;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
