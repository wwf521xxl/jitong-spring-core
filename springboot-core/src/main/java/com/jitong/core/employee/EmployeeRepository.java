package com.jitong.core.employee;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmployeeRepository extends JpaRepository<Employee, String> {

    Optional<Employee> findByEmployeeNo(int employeeNo);

    Optional<Employee> findByNameIgnoreCase(String name);

    List<Employee> findByActiveTrueOrderByEmployeeNoAsc();

    boolean existsByEmployeeNo(int employeeNo);

    long countByActiveTrueAndAdminFalseAndCanCreateJobTrue();
}
