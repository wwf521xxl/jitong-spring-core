package com.jitong.core.group;

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
@Table(name = "job_step_group_events")
@Getter
@Setter
@NoArgsConstructor
public class JobStepGroupEvent {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "group_id", nullable = false, length = 32)
    private String groupId;

    @Convert(converter = GroupStatusConverter.class)
    @Column(name = "from_state", nullable = false, length = 20)
    private GroupStatus fromState;

    @Convert(converter = GroupStatusConverter.class)
    @Column(name = "to_state", nullable = false, length = 20)
    private GroupStatus toState;

    @Column(nullable = false, length = 32)
    private String action;

    @Column(name = "actor_id", length = 32)
    private String actorId;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
