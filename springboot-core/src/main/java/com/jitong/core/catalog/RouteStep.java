package com.jitong.core.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "route_steps")
@Getter
@Setter
@NoArgsConstructor
public class RouteStep {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "route_id", nullable = false, length = 32)
    private String routeId;

    @Column(name = "step_no", nullable = false)
    private int stepNo;

    @Column(name = "process_name", nullable = false, length = 40)
    private String processName;
}
