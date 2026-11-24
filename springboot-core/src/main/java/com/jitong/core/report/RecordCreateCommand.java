package com.jitong.core.report;

import java.math.BigDecimal;

public record RecordCreateCommand(
        String jobId,
        int stepNo,
        BigDecimal qty,
        String unit,
        String note,
        String idempotencyKey,
        String source,
        String deviceId,
        String photo,
        boolean forceDuplicate
) {
}
