package com.jitong.core.job;

import java.math.BigDecimal;
import java.util.List;

public record AutoReportOptions(
        String jobId,
        String orderNo,
        String line,
        BigDecimal qty,
        String unit,
        List<Integer> stepNos,
        List<AutoStep> steps
) {
    public record AutoStep(int no, String name) {
    }
}
