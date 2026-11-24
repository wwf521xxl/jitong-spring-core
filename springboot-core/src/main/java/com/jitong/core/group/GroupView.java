package com.jitong.core.group;

import java.math.BigDecimal;
import java.util.List;

public record GroupView(
        String id,
        int stepNo,
        String status,
        BigDecimal totalQty,
        String unit,
        int memberCount,
        BigDecimal perQty,
        List<GroupMemberView> members
) {
    public record GroupMemberView(String id, String name) {
    }
}
