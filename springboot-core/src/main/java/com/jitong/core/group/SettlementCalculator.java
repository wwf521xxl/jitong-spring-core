package com.jitong.core.group;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public final class SettlementCalculator {

    public static final int SCALE = 3;

    private SettlementCalculator() {
    }

    /**
     * 按人数平分，最后一个人补小数差额，保证 sum(result) == total。
     * 例：100 / 3 -> [33.333, 33.333, 33.334]
     */
    public static List<BigDecimal> split(BigDecimal total, int count) {
        if (count <= 0) {
            return List.of();
        }
        BigDecimal safeTotal = total == null ? BigDecimal.ZERO : total;
        BigDecimal per = safeTotal.divide(BigDecimal.valueOf(count), SCALE, RoundingMode.HALF_UP);
        List<BigDecimal> result = new ArrayList<>(count);
        for (int index = 0; index < count - 1; index++) {
            result.add(per);
        }
        BigDecimal last = safeTotal
                .subtract(per.multiply(BigDecimal.valueOf(count - 1L)))
                .setScale(SCALE, RoundingMode.HALF_UP);
        result.add(last);
        return result;
    }
}
