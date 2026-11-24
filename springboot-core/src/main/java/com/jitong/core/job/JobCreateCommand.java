package com.jitong.core.job;

import java.math.BigDecimal;

public record JobCreateCommand(
        String unit,
        String line,
        String orderNo,
        String remark,
        BigDecimal meters,
        String routeKey
) {
}
