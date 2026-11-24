package com.jitong.core.employee;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class EmployeeViewAssembler {

    private static final List<String> LINE_ORDER = List.of("写真", "亚克力");
    private final EmployeeSkillRepository skillRepository;
    private final EmployeeProcessSkillRepository processSkillRepository;

    public EmployeeViewAssembler(
            EmployeeSkillRepository skillRepository,
            EmployeeProcessSkillRepository processSkillRepository
    ) {
        this.skillRepository = skillRepository;
        this.processSkillRepository = processSkillRepository;
    }

    public List<EmployeeView> toViews(List<Employee> employees) {
        if (employees.isEmpty()) {
            return List.of();
        }
        List<String> ids = employees.stream().map(Employee::getId).toList();
        Map<String, List<EmployeeSkill>> legacy = new HashMap<>();
        for (EmployeeSkill skill : skillRepository.findByEmployeeIdIn(ids)) {
            legacy.computeIfAbsent(skill.getEmployeeId(), ignored -> new ArrayList<>()).add(skill);
        }
        Map<String, List<String>> processes = new HashMap<>();
        for (EmployeeProcessSkill skill : processSkillRepository.findByEmployeeIdIn(ids)) {
            processes.computeIfAbsent(skill.getEmployeeId(), ignored -> new ArrayList<>())
                    .add(skill.getProcessName());
        }
        return employees.stream()
                .map(employee -> toView(employee,
                        legacy.getOrDefault(employee.getId(), List.of()),
                        processes.getOrDefault(employee.getId(), List.of())))
                .toList();
    }

    public EmployeeView toView(Employee employee) {
        return toView(employee,
                skillRepository.findByEmployeeIdOrderByLineAscStepNoAsc(employee.getId()),
                processSkillRepository.findByEmployeeIdOrderByProcessNameAsc(employee.getId()).stream()
                        .map(EmployeeProcessSkill::getProcessName)
                        .toList());
    }

    private EmployeeView toView(
            Employee employee,
            List<EmployeeSkill> legacySkills,
            List<String> processSkills
    ) {
        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        legacySkills.stream()
                .sorted(Comparator
                        .comparingInt((EmployeeSkill skill) -> lineIndex(skill.getLine()))
                        .thenComparingInt(EmployeeSkill::getStepNo))
                .forEach(skill -> grouped.computeIfAbsent(skill.getLine(), ignored -> new ArrayList<>())
                        .add(skill.getStepNo()));
        List<EmployeeView.SkillLineView> skills = grouped.entrySet().stream()
                .map(entry -> new EmployeeView.SkillLineView(entry.getKey(), List.copyOf(entry.getValue())))
                .toList();
        String avatar = employee.getAvatar();
        if (avatar == null || avatar.isBlank()) {
            avatar = employee.getName() == null || employee.getName().isEmpty()
                    ? "" : employee.getName().substring(0, 1);
        }
        return new EmployeeView(
                employee.getId(),
                employee.getEmployeeNo(),
                employee.getName(),
                employee.getRole(),
                avatar,
                employee.isAdmin(),
                employee.isCanCreateJob(),
                skills,
                processSkills.stream().sorted().toList()
        );
    }

    private int lineIndex(String line) {
        int index = LINE_ORDER.indexOf(line);
        return index < 0 ? 99 : index;
    }
}
