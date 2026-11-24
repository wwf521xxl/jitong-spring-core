package com.jitong.core.catalog;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteStepRepository extends JpaRepository<RouteStep, Long> {

    List<RouteStep> findByRouteIdOrderByStepNoAsc(String routeId);

    List<RouteStep> findByRouteIdInOrderByRouteIdAscStepNoAsc(Collection<String> routeIds);

    long countByProcessName(String processName);

    @Modifying
    @Query("delete from RouteStep s where s.routeId = :routeId")
    void deleteByRouteIdBulk(@Param("routeId") String routeId);
}
