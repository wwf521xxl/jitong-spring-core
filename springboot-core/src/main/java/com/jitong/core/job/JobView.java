package com.jitong.core.job;

import com.jitong.core.group.GroupView;
import java.math.BigDecimal;
import java.util.List;

public record JobView(
        String id,
        String orderNo,
        String line,
        String product,
        String spec,
        String customer,
        BigDecimal plannedQty,
        BigDecimal meters,
        String unit,
        String due,
        String priority,
        String remark,
        String routeKey,
        String routeName,
        String createdBy,
        String status,
        String qr,
        List<JobStepView> steps
) {
    public record JobStepView(
            int no,
            String name,
            String processName,
            BigDecimal planned,
            String unit,
            GroupView group
    ) {
    }
}
