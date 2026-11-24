package com.jitong.core.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "employees")
@Getter
@Setter
@NoArgsConstructor
public class Employee {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "employee_no", nullable = false, unique = true)
    private int employeeNo;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(nullable = false, length = 60)
    private String role;

    @Column(length = 8)
    private String avatar;

    @Column(name = "is_admin", nullable = false)
    private boolean admin;

    @Column(name = "can_create_job", nullable = false)
    private boolean canCreateJob;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "pin_hash", nullable = false, length = 255)
    private String pinHash;

    @Column(name = "token_version", nullable = false)
    private int tokenVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
