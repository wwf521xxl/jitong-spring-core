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

/** 兼容权限模型：产线 + 第几道。新模型按工序名称授权，这里保留以兼容历史数据。 */
@Entity
@Table(name = "employee_skills")
@Getter
@Setter
@NoArgsConstructor
public class EmployeeSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, length = 32)
    private String employeeId;

    @Column(nullable = false, length = 20)
    private String line;

    @Column(name = "step_no", nullable = false)
    private int stepNo;
}
