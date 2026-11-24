package com.jitong.core.audit;

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
@Table(name = "audit_log")
@Getter
@Setter
@NoArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_id", length = 32)
    private String actorId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "entity_type", length = 32)
    private String entityType;

    @Column(name = "entity_id", length = 64)
    private String entityId;

    @Column(length = 500)
    private String detail;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
