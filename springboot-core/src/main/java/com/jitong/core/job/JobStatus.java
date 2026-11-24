package com.jitong.core.job;

public enum JobStatus {
    ACTIVE,
    COMPLETED,
    VOID;

    public String wireValue() {
        return name().toLowerCase();
    }
}
