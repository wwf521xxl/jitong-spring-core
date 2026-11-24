package com.jitong.core.report;

public record RecordView(
        String id,
        String jobId,
        int stepNo,
        String employeeId,
        java.math.BigDecimal qty,
        String unit,
        String at,
        String status,
        String note,
        String source,
        boolean synced,
        String photo,
        String approvedBy,
        String approvedAt
) {
}
