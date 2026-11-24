package com.jitong.core.job;

import java.math.BigDecimal;

public record JobEditCommand(String orderNo, String remark, BigDecimal meters) {
}
