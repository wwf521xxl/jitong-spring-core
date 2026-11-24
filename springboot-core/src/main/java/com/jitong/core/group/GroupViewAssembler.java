package com.jitong.core.group;

import com.jitong.core.employee.Employee;
import com.jitong.core.employee.EmployeeRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GroupViewAssembler {

    private final JobStepGroupMemberRepository memberRepository;
    private final EmployeeRepository employeeRepository;

    public List<GroupView> toViews(List<JobStepGroup> groups) {
        if (groups.isEmpty()) {
            return List.of();
        }
        List<String> groupIds = groups.stream().map(JobStepGroup::getId).toList();
        Map<String, List<JobStepGroupMember>> membersByGroup = new LinkedHashMap<>();
        List<String> employeeIds = new ArrayList<>();
        for (JobStepGroupMember member : memberRepository.findByGroupIdInOrderByJoinedAtAsc(groupIds)) {
            membersByGroup.computeIfAbsent(member.getGroupId(), ignored -> new ArrayList<>()).add(member);
            employeeIds.add(member.getEmployeeId());
        }
        Map<String, String> names = new HashMap<>();
        for (Employee employee : employeeRepository.findAllById(employeeIds)) {
            names.put(employee.getId(), employee.getName());
        }
        return groups.stream().map(group -> toView(group, membersByGroup, names)).toList();
    }

    public GroupView toView(JobStepGroup group) {
        return toViews(List.of(group)).get(0);
    }

    private GroupView toView(
            JobStepGroup group,
            Map<String, List<JobStepGroupMember>> membersByGroup,
            Map<String, String> names
    ) {
        List<JobStepGroupMember> members = membersByGroup.getOrDefault(group.getId(), List.of());
        BigDecimal total = group.getTotalQty() == null ? BigDecimal.ZERO : group.getTotalQty();
        int count = members.size();
        BigDecimal per = count == 0
                ? BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP)
                : total.divide(BigDecimal.valueOf(count), 3, RoundingMode.HALF_UP);
        List<GroupView.GroupMemberView> memberViews = members.stream()
                .map(member -> new GroupView.GroupMemberView(
                        member.getEmployeeId(),
                        names.getOrDefault(member.getEmployeeId(), member.getEmployeeId())))
                .toList();
        return new GroupView(
                group.getId(),
                group.getStepNo(),
                group.getStatus().wireValue(),
                total,
                group.getUnit() == null ? "" : group.getUnit(),
                count,
                per,
                memberViews
        );
    }
}
