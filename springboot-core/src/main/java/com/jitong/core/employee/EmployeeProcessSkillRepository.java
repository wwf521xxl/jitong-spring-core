package com.jitong.core.employee;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EmployeeProcessSkillRepository extends JpaRepository<EmployeeProcessSkill, Long> {

    List<EmployeeProcessSkill> findByEmployeeIdOrderByProcessNameAsc(String employeeId);

    List<EmployeeProcessSkill> findByEmployeeIdIn(Collection<String> employeeIds);

    boolean existsByEmployeeIdAndProcessName(String employeeId, String processName);

    long countByProcessName(String processName);

    @Modifying
    @Query("delete from EmployeeProcessSkill s where s.employeeId = :employeeId")
    void deleteByEmployeeIdBulk(@Param("employeeId") String employeeId);
}
