package com.jitong.core.system;

import com.jitong.core.audit.AuditLog;
import com.jitong.core.audit.AuditService;
import com.jitong.core.bootstrap.BootstrapResponse;
import com.jitong.core.bootstrap.BootstrapService;
import com.jitong.core.catalog.CatalogService;
import com.jitong.core.catalog.RouteCommand;
import com.jitong.core.common.ApiException;
import com.jitong.core.common.TimeFormat;
import com.jitong.core.employee.Employee;
import com.jitong.core.employee.EmployeeService;
import com.jitong.core.employee.NewEmployeeCommand;
import com.jitong.core.security.UserPrincipal;
import jakarta.validation.Valid;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final EmployeeService employeeService;
    private final CatalogService catalogService;
    private final ClearDataService clearDataService;
    private final BootstrapService bootstrapService;
    private final AuditService auditService;

    @PostMapping("/clear-data")
    public Map<String, Object> clearData(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody ClearDataRequest body
    ) {
        String backup = clearDataService.clear(user, body.password(), body.confirm());
        BootstrapResponse data = bootstrapService.build(user);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("backup", backup);
        result.put("data", data);
        return result;
    }

    @PostMapping("/skills")
    public BootstrapResponse addSkill(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody SkillRequest body
    ) {
        catalogService.addSkill(user, body.name());
        return bootstrapService.build(user);
    }

    @PatchMapping("/skills/{name}")
    public BootstrapResponse renameSkill(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String name,
            @RequestBody SkillRequest body
    ) {
        catalogService.renameSkill(user, name,
                body.displayName() == null ? body.name() : body.displayName());
        return bootstrapService.build(user);
    }

    @DeleteMapping("/skills/{name}")
    public BootstrapResponse deleteSkill(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String name
    ) {
        catalogService.deleteSkill(user, name);
        return bootstrapService.build(user);
    }

    @PostMapping("/routes")
    public BootstrapResponse addRoute(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody RouteRequest body
    ) {
        catalogService.saveRoute(user, null, new RouteCommand(body.line(), body.name(), body.steps()));
        return bootstrapService.build(user);
    }

    @PatchMapping("/routes/{routeKey}")
    public BootstrapResponse editRoute(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String routeKey,
            @RequestBody RouteRequest body
    ) {
        catalogService.saveRoute(user, routeKey, new RouteCommand(body.line(), body.name(), body.steps()));
        return bootstrapService.build(user);
    }

    @DeleteMapping("/routes/{routeKey}")
    public BootstrapResponse deleteRoute(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String routeKey
    ) {
        catalogService.deleteRoute(user, routeKey);
        return bootstrapService.build(user);
    }

    @PostMapping("/employees")
    public BootstrapResponse addEmployee(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody NewEmployeeRequest body
    ) {
        employeeService.add(user, new NewEmployeeCommand(
                body.name(), body.employeeNo(), body.pin(), body.role(),
                body.canCreateJob(), body.processSkills()));
        return bootstrapService.build(user);
    }

    @PatchMapping("/employees/{employeeId}")
    public BootstrapResponse editEmployee(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String employeeId,
            @RequestBody EmployeeEditRequest body
    ) {
        employeeService.update(user, employeeId, body.name(), body.role());
        return bootstrapService.build(user);
    }

    @PostMapping("/employees/{employeeId}/deactivate")
    public BootstrapResponse deactivateEmployee(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String employeeId
    ) {
        employeeService.deactivate(user, employeeId);
        return bootstrapService.build(user);
    }

    @PutMapping("/employees/{employeeId}/process-skills")
    public BootstrapResponse setProcessSkills(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String employeeId,
            @RequestBody ProcessSkillsRequest body
    ) {
        employeeService.setProcessSkills(user, employeeId, body.processes());
        return bootstrapService.build(user);
    }

    @PostMapping("/employees/{employeeId}/reset-pin")
    public Map<String, Object> resetPin(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String employeeId
    ) {
        String pin = employeeService.resetPin(user, employeeId);
        return Map.of("employeeId", employeeId, "pin", pin);
    }

    @PostMapping("/employees/{employeeId}/create-permission")
    public BootstrapResponse setCreatePermission(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String employeeId,
            @RequestBody CreatePermissionRequest body
    ) {
        employeeService.setCreatePermission(user, employeeId, body.canCreateJob());
        return bootstrapService.build(user);
    }

    @GetMapping("/audit")
    public List<Map<String, Object>> audit(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam(defaultValue = "100") int limit
    ) {
        return auditService.list(limit).stream().map(log -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", log.getId());
            row.put("actor_id", log.getActorId());
            row.put("action", log.getAction());
            row.put("entity_type", log.getEntityType());
            row.put("entity_id", log.getEntityId());
            row.put("detail", log.getDetail());
            row.put("created_at", TimeFormat.iso(log.getCreatedAt()));
            return row;
        }).toList();
    }

    public record ClearDataRequest(String password, String confirm) {
    }

    public record SkillRequest(String name, String displayName) {
    }

    public record RouteRequest(String line, String name, List<String> steps) {
    }

    public record NewEmployeeRequest(
            String name,
            int employeeNo,
            String pin,
            String role,
            boolean canCreateJob,
            List<String> processSkills
    ) {
    }

    public record EmployeeEditRequest(String name, String role) {
    }

    public record ProcessSkillsRequest(List<String> processes) {
    }

    public record CreatePermissionRequest(boolean canCreateJob) {
    }
}
