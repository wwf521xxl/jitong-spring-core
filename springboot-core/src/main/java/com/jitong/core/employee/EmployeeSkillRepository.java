package com.jitong.core.employee;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, Long> {

    List<EmployeeSkill> findByEmployeeIdOrderByLineAscStepNoAsc(String employeeId);

    List<EmployeeSkill> findByEmployeeIdIn(Collection<String> employeeIds);

    void deleteByEmployeeId(String employeeId);
}
