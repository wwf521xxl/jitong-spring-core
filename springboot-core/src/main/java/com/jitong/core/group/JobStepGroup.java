package com.jitong.core.group;

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
@Table(name = "job_step_groups")
@Getter
@Setter
@NoArgsConstructor
public class JobStepGroup {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "job_id", nullable = false, length = 32)
    private String jobId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Convert(converter = GroupStatusConverter.class)
    @Column(nullable = false, length = 20)
    private GroupStatus status = GroupStatus.OPEN;

    @Column(name = "total_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal totalQty = BigDecimal.ZERO;

    @Column(length = 10)
    private String unit;

    @Column(name = "created_by", length = 32)
    private String createdBy;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "closed_by", length = 32)
    private String closedBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_reason", length = 200)
    private String closeReason;

    @Version
    @Column(nullable = false)
    private Long version;
}
