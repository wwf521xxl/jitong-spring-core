package com.jitong.core.catalog;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillCatalogRepository extends JpaRepository<SkillCatalog, String> {

    List<SkillCatalog> findAllByOrderBySortAscNameAsc();

    Optional<SkillCatalog> findByName(String name);
}
