package com.jitong.core.job;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "production_jobs")
@Getter
@Setter
@NoArgsConstructor
public class ProductionJob {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "order_no", nullable = false, unique = true, length = 64)
    private String orderNo;

    @Column(nullable = false, length = 20)
    private String line;

    @Column(length = 100)
    private String product;

    @Column(length = 200)
    private String spec;

    @Column(length = 100)
    private String customer;

    @Column(name = "planned_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal plannedQty = BigDecimal.ZERO;

    @Column(precision = 12, scale = 3)
    private BigDecimal meters;

    @Column(nullable = false, length = 10)
    private String unit;

    @Column(name = "due_date", length = 20)
    private String dueDate;

    @Column(nullable = false, length = 10)
    private String priority = "正常";

    @Column(length = 500)
    private String remark;

    @Column(name = "route_key", length = 20)
    private String routeKey;

    @Convert(converter = JobStatusConverter.class)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.ACTIVE;

    @Column(name = "qr_token", nullable = false, unique = true, length = 80)
    private String qrToken;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private Long version;
}
