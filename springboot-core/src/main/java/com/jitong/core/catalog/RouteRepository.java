package com.jitong.core.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RouteRepository extends JpaRepository<Route, String> {

    List<Route> findByActiveTrueOrderBySortAscRouteKeyAsc();

    Optional<Route> findByRouteKey(String routeKey);

    Optional<Route> findByRouteKeyAndActiveTrue(String routeKey);

    List<Route> findByRouteKeyIn(Collection<String> routeKeys);
}
