package com.jitong.core.group;

import java.util.Locale;

public enum GroupStatus {
    OPEN,
    SETTLING,
    CLOSED,
    CANCELLED;

    public String wireValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean terminal() {
        return this == CLOSED || this == CANCELLED;
    }
}
