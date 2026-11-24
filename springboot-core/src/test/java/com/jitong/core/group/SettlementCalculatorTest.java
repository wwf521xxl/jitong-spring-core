package com.jitong.core.group;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class SettlementCalculatorTest {

    @Test
    void splitsWithLastMemberCarryingRoundingDifference() {
        List<BigDecimal> shares = SettlementCalculator.split(new BigDecimal("100"), 3);

        assertThat(shares).containsExactly(
                new BigDecimal("33.333"),
                new BigDecimal("33.333"),
                new BigDecimal("33.334")
        );
        assertThat(shares.stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.000");
    }

    @Test
    void splitsMetersExactly() {
        List<BigDecimal> shares = SettlementCalculator.split(new BigDecimal("48.5"), 2);

        assertThat(shares).containsExactly(new BigDecimal("24.250"), new BigDecimal("24.250"));
    }

    @Test
    void zeroMembersReturnsEmptyList() {
        assertThat(SettlementCalculator.split(new BigDecimal("10"), 0)).isEmpty();
    }

    @Test
    void zeroTotalStillProducesOneRecordPerMember() {
        List<BigDecimal> shares = SettlementCalculator.split(BigDecimal.ZERO, 2);

        assertThat(shares).containsExactly(new BigDecimal("0.000"), new BigDecimal("0.000"));
    }
}
