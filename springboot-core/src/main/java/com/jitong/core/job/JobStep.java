package com.jitong.core.job;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_steps")
@Getter
@Setter
@NoArgsConstructor
public class JobStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 32)
    private String jobId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(name = "step_name", length = 40)
    private String stepName;

    @Column(name = "planned_qty", nullable = false, precision = 12, scale = 3)
    private BigDecimal plannedQty = BigDecimal.ZERO;

    @Column(nullable = false, length = 10)
    private String unit;
}
