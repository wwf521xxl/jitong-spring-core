package com.jitong.core.audit;

import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository repository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(String actorId, String action, String entityType, String entityId, String detail) {
        AuditLog log = new AuditLog();
        log.setActorId(limit(actorId, 32));
        log.setAction(limit(action, 64));
        log.setEntityType(limit(entityType, 32));
        log.setEntityId(limit(entityId, 64));
        log.setDetail(limit(detail == null ? "" : detail, 500));
        log.setCreatedAt(Instant.now(clock));
        repository.save(log);
    }

    private String limit(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    @Transactional(readOnly = true)
    public java.util.List<AuditLog> list(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 500));
        return repository.findAllByOrderByIdDesc(org.springframework.data.domain.PageRequest.of(0, safeLimit));
    }
}
