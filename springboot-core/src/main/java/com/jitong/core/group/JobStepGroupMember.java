package com.jitong.core.group;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_step_group_members")
@Getter
@Setter
@NoArgsConstructor
public class JobStepGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", nullable = false, length = 32)
    private String groupId;

    @Column(name = "employee_id", nullable = false, length = 32)
    private String employeeId;

    @Column(name = "joined_at")
    private Instant joinedAt;

    @Column(name = "record_id", length = 32)
    private String recordId;
}
