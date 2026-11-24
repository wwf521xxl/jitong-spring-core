package com.jitong.core.report;

import java.util.Locale;

public enum CorrectionStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }
}
