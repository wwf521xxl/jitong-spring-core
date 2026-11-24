package com.jitong.core.employee;

import java.util.List;

public record NewEmployeeCommand(
        String name,
        int employeeNo,
        String pin,
        String role,
        boolean canCreateJob,
        List<String> processSkills
) {
}
