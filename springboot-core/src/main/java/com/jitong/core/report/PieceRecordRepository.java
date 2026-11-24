package com.jitong.core.report;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PieceRecordRepository extends JpaRepository<PieceRecord, String> {

    Optional<PieceRecord> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    boolean existsByJobIdAndStepNoAndStatusNot(String jobId, int stepNo, RecordStatus status);

    boolean existsByJobIdAndStatusNot(String jobId, RecordStatus status);

    List<PieceRecord> findByJobIdAndStatusNot(String jobId, RecordStatus status);

    long countByJobIdAndStatusNot(String jobId, RecordStatus status);

    long countByJobIdAndStepNoAndStatus(String jobId, int stepNo, RecordStatus status);

    List<PieceRecord> findByJobIdAndStepNoAndStatusNotOrderByCreatedAtAsc(
            String jobId, int stepNo, RecordStatus status);

    List<PieceRecord> findByGroupIdOrderByEmployeeIdAsc(String groupId);

    @Query("""
            select coalesce(sum(r.qty), 0) from PieceRecord r
            where r.jobId = :jobId and r.stepNo = :stepNo and r.unit = :unit and r.status = :status
            """)
    BigDecimal sumQty(
            @Param("jobId") String jobId,
            @Param("stepNo") int stepNo,
            @Param("unit") String unit,
            @Param("status") RecordStatus status
    );

    Optional<PieceRecord> findFirstByEmployeeIdAndJobIdAndStepNoAndQtyAndStatusNotAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
            String employeeId,
            String jobId,
            int stepNo,
            BigDecimal qty,
            RecordStatus status,
            Instant cutoff
    );

    @Query("""
            select r from PieceRecord r
            where r.createdAt >= :cutoff
               or r.jobId in (select j.id from ProductionJob j where j.status = :active)
            order by r.createdAt desc
            """)
    List<PieceRecord> findBootstrap(
            @Param("cutoff") Instant cutoff,
            @Param("active") com.jitong.core.job.JobStatus active,
            Pageable pageable
    );
}
