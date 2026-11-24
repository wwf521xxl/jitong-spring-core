package com.jitong.core.report;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "corrections")
@Getter
@Setter
@NoArgsConstructor
public class Correction {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "record_id", nullable = false, length = 32)
    private String recordId;

    @Column(nullable = false, length = 64)
    private String reason;

    @Column(length = 500)
    private String note;

    @Convert(converter = CorrectionStatusConverter.class)
    @Column(nullable = false, length = 20)
    private CorrectionStatus status = CorrectionStatus.PENDING;

    @Column(name = "requested_by", nullable = false, length = 32)
    private String requestedBy;

    @Column(name = "approved_by", length = 32)
    private String approvedBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
