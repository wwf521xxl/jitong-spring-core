package com.jitong.core.job;

import java.math.BigDecimal;

public record JobEditRequest(String orderNo, String remark, BigDecimal meters) {
}
