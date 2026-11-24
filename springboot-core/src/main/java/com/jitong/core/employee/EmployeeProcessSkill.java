package com.jitong.core.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 新版权限模型：按工序名称授权，路线调整后不会错位。 */
@Entity
@Table(name = "employee_process_skills")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeProcessSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 32)
    private String employeeId;

    @Column(name = "process_name", nullable = false, length = 40)
    private String processName;
}
