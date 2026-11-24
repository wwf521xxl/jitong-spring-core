package com.jitong.core.job;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductionJobRepository extends JpaRepository<ProductionJob, String> {

    Optional<ProductionJob> findByOrderNo(String orderNo);

    boolean existsByOrderNo(String orderNo);

    boolean existsByQrToken(String qrToken);

    long countByRouteKey(String routeKey);

    @Query("""
            select j from ProductionJob j
            where j.createdAt >= :cutoff or j.status = :active
            order by j.createdAt desc
            """)
    List<ProductionJob> findBootstrap(@Param("cutoff") Instant cutoff, @Param("active") JobStatus active);
}
