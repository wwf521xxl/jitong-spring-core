package com.jitong.core.report;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "piece_records")
@Getter
@Setter
@NoArgsConstructor
public class PieceRecord {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "job_id", nullable = false, length = 32)
    private String jobId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(nullable = false, length = 20)
    private String line;

    @Column(name = "employee_id", nullable = false, length = 32)
    private String employeeId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal qty;

    @Column(nullable = false, length = 10)
    private String unit;

    @Convert(converter = RecordStatusConverter.class)
    @Column(nullable = false, length = 20)
    private RecordStatus status = RecordStatus.VALID;

    @Column(length = 500)
    private String note;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Column(nullable = false, length = 20)
    private String source = "scan";

    @Column(name = "photo_path", length = 200)
    private String photoPath;

    @Column(name = "device_id", length = 64)
    private String deviceId;

    @Column(name = "group_id", length = 32)
    private String groupId;

    @Column(name = "approved_by", length = 32)
    private String approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
