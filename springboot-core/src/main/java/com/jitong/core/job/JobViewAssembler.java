package com.jitong.core.job;

import com.jitong.core.catalog.CatalogService;
import com.jitong.core.catalog.Route;
import com.jitong.core.catalog.RouteRepository;
import com.jitong.core.group.GroupView;
import com.jitong.core.group.GroupViewAssembler;
import com.jitong.core.group.JobStepGroup;
import com.jitong.core.group.JobStepGroupRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobViewAssembler {

    private final JobStepRepository stepRepository;
    private final JobStepGroupRepository groupRepository;
    private final GroupViewAssembler groupViewAssembler;
    private final RouteRepository routeRepository;
    private final CatalogService catalogService;

    public List<JobView> assemble(List<ProductionJob> jobs) {
        if (jobs.isEmpty()) {
            return List.of();
        }
        List<String> jobIds = jobs.stream().map(ProductionJob::getId).toList();
        Map<String, List<JobStep>> stepsByJob = new LinkedHashMap<>();
        for (JobStep step : stepRepository.findByJobIdInOrderByJobIdAscStepNoAsc(jobIds)) {
            stepsByJob.computeIfAbsent(step.getJobId(), ignored -> new ArrayList<>()).add(step);
        }
        Map<String, GroupView> groupsByKey = new HashMap<>();
        List<JobStepGroup> groups = groupRepository.findByJobIdIn(jobIds);
        List<GroupView> groupViews = groupViewAssembler.toViews(groups);
        for (int index = 0; index < groups.size(); index++) {
            groupsByKey.put(key(groups.get(index).getJobId(), groups.get(index).getStepNo()),
                    groupViews.get(index));
        }
        Map<String, String> routeNames = new HashMap<>();
        List<String> routeKeys = jobs.stream()
                .map(ProductionJob::getRouteKey)
                .filter(key -> key != null && !key.isBlank())
                .distinct()
                .toList();
        if (!routeKeys.isEmpty()) {
            for (Route route : routeRepository.findByRouteKeyIn(routeKeys)) {
                routeNames.put(route.getRouteKey(), route.getName());
            }
        }
        Map<String, String> displayNames = catalogService.displayNames();

        return jobs.stream().map(job -> toView(job,
                stepsByJob.getOrDefault(job.getId(), List.of()),
                groupsByKey,
                routeNames,
                displayNames)).toList();
    }

    public JobView assemble(ProductionJob job) {
        return assemble(List.of(job)).get(0);
    }

    private JobView toView(
            ProductionJob job,
            List<JobStep> steps,
            Map<String, GroupView> groupsByKey,
            Map<String, String> routeNames,
            Map<String, String> displayNames
    ) {
        String routeKey = job.getRouteKey();
        if (routeKey == null || routeKey.isBlank()) {
            routeKey = "写真".equals(job.getLine()) ? "PHOTO" : "";
        }
        String routeName = routeNames.get(routeKey);
        if (routeName == null) {
            routeName = routeKey == null || routeKey.isBlank() ? "旧订单流程" : routeKey;
        }
        List<JobView.JobStepView> stepViews = steps.stream()
                .map(step -> new JobView.JobStepView(
                        step.getStepNo(),
                        displayName(displayNames, step.getStepName(), step.getStepNo()),
                        step.getStepName() == null ? "" : step.getStepName(),
                        step.getPlannedQty(),
                        step.getUnit(),
                        groupsByKey.get(key(job.getId(), step.getStepNo()))))
                .toList();
        return new JobView(
                job.getId(),
                job.getOrderNo(),
                job.getLine(),
                safe(job.getProduct()),
                safe(job.getSpec()),
                safe(job.getCustomer()),
                job.getPlannedQty(),
                job.getMeters() == null ? java.math.BigDecimal.ZERO : job.getMeters(),
                job.getUnit(),
                safe(job.getDueDate()),
                job.getPriority() == null ? "正常" : job.getPriority(),
                safe(job.getRemark()),
                routeKey,
                routeName,
                safe(job.getCreatedBy()),
                job.getStatus().wireValue(),
                job.getQrToken(),
                stepViews
        );
    }

    private String displayName(Map<String, String> displayNames, String processName, int stepNo) {
        if (processName == null || processName.isBlank()) {
            return "第" + stepNo + "道";
        }
        return displayNames.getOrDefault(processName, processName);
    }

    private String key(String jobId, int stepNo) {
        return jobId + "#" + stepNo;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
