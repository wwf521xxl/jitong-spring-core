package com.jitong.core.employee;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record EmployeeView(
        String id,
        int employeeNo,
        String name,
        String role,
        String avatar,
        @JsonProperty("isAdmin") boolean isAdmin,
        @JsonProperty("canCreateJob") boolean canCreateJob,
        List<SkillLineView> skills,
        List<String> processSkills
) {
    public record SkillLineView(String line, List<Integer> steps) {
    }
}
