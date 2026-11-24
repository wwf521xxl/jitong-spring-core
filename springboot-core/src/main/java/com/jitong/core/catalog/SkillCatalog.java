package com.jitong.core.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "skill_catalog")
@Getter
@Setter
@NoArgsConstructor
public class SkillCatalog {

    @Id
    @Column(length = 40)
    private String name;

    @Column(name = "display_name", nullable = false, length = 40)
    private String displayName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sort;

    @Column(name = "created_at")
    private Instant createdAt;
}
