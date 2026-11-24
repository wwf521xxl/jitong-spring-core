package com.jitong.core.report;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CorrectionRepository extends JpaRepository<Correction, String> {

    boolean existsByRecordIdAndStatus(String recordId, CorrectionStatus status);

    List<Correction> findByRecordIdAndStatus(String recordId, CorrectionStatus status);

    List<Correction> findByRecordIdInAndStatus(Collection<String> recordIds, CorrectionStatus status);

    List<Correction> findByStatus(CorrectionStatus status);

    @Query("""
            select c from Correction c
            where c.createdAt >= :cutoff
            order by c.createdAt desc
            """)
    List<Correction> findBootstrap(@Param("cutoff") Instant cutoff, Pageable pageable);
}
