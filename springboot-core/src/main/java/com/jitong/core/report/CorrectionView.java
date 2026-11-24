package com.jitong.core.report;

public record CorrectionView(
        String id,
        String recordId,
        String employeeId,
        String reason,
        String note,
        String status,
        String approvedBy,
        String at
) {
}
