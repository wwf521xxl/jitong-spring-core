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
@Table(name = "routes")
@Getter
@Setter
@NoArgsConstructor
public class Route {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "route_key", nullable = false, unique = true, length = 20)
    private String routeKey;

    @Column(nullable = false, length = 20)
    private String line;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int sort;

    @Column(name = "created_at")
    private Instant createdAt;
}
