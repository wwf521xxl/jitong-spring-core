package com.jitong.core.catalog;

import com.jitong.core.audit.AuditService;
import com.jitong.core.common.ApiException;
import com.jitong.core.common.Ids;
import com.jitong.core.employee.EmployeeProcessSkillRepository;
import com.jitong.core.job.JobStepRepository;
import com.jitong.core.job.ProductionJobRepository;
import com.jitong.core.security.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CatalogService {

    private final SkillCatalogRepository skillRepository;
    private final RouteRepository routeRepository;
    private final RouteStepRepository routeStepRepository;
    private final EmployeeProcessSkillRepository employeeProcessSkillRepository;
    private final JobStepRepository jobStepRepository;
    private final ProductionJobRepository jobRepository;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<SkillView> skills() {
        return skillRepository.findAllByOrderBySortAscNameAsc().stream()
                .map(skill -> new SkillView(skill.getName(), skill.getDisplayName(), skill.isActive()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RouteView> routes() {
        List<Route> routes = routeRepository.findByActiveTrueOrderBySortAscRouteKeyAsc();
        if (routes.isEmpty()) {
            return List.of();
        }
        Map<String, String> displayNames = displayNames();
        Map<String, List<RouteStep>> stepsByRoute = new HashMap<>();
        for (RouteStep step : routeStepRepository.findByRouteIdInOrderByRouteIdAscStepNoAsc(
                routes.stream().map(Route::getId).toList())) {
            stepsByRoute.computeIfAbsent(step.getRouteId(), ignored -> new ArrayList<>()).add(step);
        }
        return routes.stream()
                .map(route -> new RouteView(
                        route.getRouteKey(),
                        route.getLine(),
                        route.getName(),
                        stepsByRoute.getOrDefault(route.getId(), List.of()).stream()
                                .map(step -> new RouteStepView(
                                        step.getProcessName(),
                                        displayNames.getOrDefault(step.getProcessName(), step.getProcessName())))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, String> displayNames() {
        Map<String, String> result = new LinkedHashMap<>();
        for (SkillCatalog skill : skillRepository.findAllByOrderBySortAscNameAsc()) {
            result.put(skill.getName(), skill.getDisplayName());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public String displayName(String processName) {
        if (processName == null || processName.isBlank()) {
            return "";
        }
        return skillRepository.findByName(processName)
                .map(SkillCatalog::getDisplayName)
                .orElse(processName);
    }

    @Transactional(readOnly = true)
    public Set<String> activeSkillNames() {
        Set<String> result = new LinkedHashSet<>();
        for (SkillCatalog skill : skillRepository.findAllByOrderBySortAscNameAsc()) {
            if (skill.isActive()) {
                result.add(skill.getName());
            }
        }
        return result;
    }

    @Transactional
    public void addSkill(UserPrincipal actor, String rawName) {
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("name_required", "技能名称不能为空");
        }
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        if (skillRepository.existsById(name)) {
            throw ApiException.conflict("skill_exists", "技能名称已存在");
        }
        SkillCatalog skill = new SkillCatalog();
        skill.setName(name);
        skill.setDisplayName(name);
        skill.setActive(true);
        skill.setSort(999);
        skill.setCreatedAt(Instant.now(clock));
        skillRepository.save(skill);
        auditService.record(actor.id(), "add_skill", "skill", name, name);
    }

    @Transactional
    public void renameSkill(UserPrincipal actor, String name, String rawDisplayName) {
        SkillCatalog skill = skillRepository.findByName(name)
                .orElseThrow(() -> ApiException.notFound("not_found", "技能不存在"));
        String displayName = rawDisplayName == null ? "" : rawDisplayName.trim();
        if (displayName.isEmpty()) {
            throw ApiException.badRequest("name_required", "显示名称不能为空");
        }
        if (displayName.length() > 40) {
            displayName = displayName.substring(0, 40);
        }
        skill.setDisplayName(displayName);
        skillRepository.save(skill);
        auditService.record(actor.id(), "rename_skill", "skill", name, displayName);
    }

    @Transactional
    public void deleteSkill(UserPrincipal actor, String name) {
        if (skillRepository.findByName(name).isEmpty()) {
            throw ApiException.notFound("not_found", "技能不存在");
        }
        long assigned = employeeProcessSkillRepository.countByProcessName(name);
        long routeUsed = routeStepRepository.countByProcessName(name);
        long jobUsed = jobStepRepository.countByStepName(name);
        if (assigned > 0 || routeUsed > 0 || jobUsed > 0) {
            throw ApiException.badRequest("skill_in_use", "该技能已被员工、路线或订单使用，只能停用，不能删除");
        }
        skillRepository.deleteById(name);
        auditService.record(actor.id(), "delete_skill", "skill", name, name);
    }

    @Transactional
    public void saveRoute(UserPrincipal actor, String routeKey, RouteCommand command) {
        String line = command.line() == null ? "" : command.line().trim();
        String name = command.name() == null ? "" : command.name().trim();
        List<String> steps = command.steps() == null ? List.of() : command.steps().stream()
                .map(value -> value == null ? "" : value.trim())
                .filter(value -> !value.isEmpty())
                .toList();
        if (!Set.of("写真", "亚克力").contains(line) || name.isEmpty() || steps.isEmpty()) {
            throw ApiException.badRequest("bad_route", "路线名称、产线和工序不能为空");
        }
        if (name.length() > 60) {
            name = name.substring(0, 60);
        }
        Set<String> allowed = activeSkillNames();
        for (String step : steps) {
            if (!allowed.contains(step)) {
                throw ApiException.badRequest("unknown_process", "路线中有未知工序：" + step);
            }
        }

        Route route;
        boolean creating = routeKey == null || routeKey.isBlank();
        if (creating) {
            String newKey;
            do {
                newKey = Ids.routeKey();
            } while (routeRepository.findByRouteKey(newKey).isPresent());
            route = new Route();
            route.setId(Ids.routeId(newKey));
            route.setRouteKey(newKey);
            route.setActive(true);
            route.setSort(999);
            route.setCreatedAt(Instant.now(clock));
        } else {
            route = routeRepository.findByRouteKey(routeKey)
                    .orElseThrow(() -> ApiException.notFound("not_found", "路线不存在"));
            routeStepRepository.deleteByRouteIdBulk(route.getId());
        }
        route.setLine(line);
        route.setName(name);
        routeRepository.save(route);

        for (int index = 0; index < steps.size(); index++) {
            RouteStep step = new RouteStep();
            step.setRouteId(route.getId());
            step.setStepNo(index + 1);
            step.setProcessName(steps.get(index));
            routeStepRepository.save(step);
        }
        auditService.record(actor.id(), "save_route", "route", route.getRouteKey(),
                line + "/" + name + "/" + steps.size());
    }

    @Transactional
    public void deleteRoute(UserPrincipal actor, String routeKey) {
        Route route = routeRepository.findByRouteKey(routeKey)
                .orElseThrow(() -> ApiException.notFound("not_found", "路线不存在"));
        if (jobRepository.countByRouteKey(routeKey) > 0) {
            throw ApiException.badRequest("route_in_use", "该路线已被订单使用，不能删除");
        }
        routeRepository.delete(route);
        auditService.record(actor.id(), "delete_route", "route", routeKey, route.getName());
    }
}
