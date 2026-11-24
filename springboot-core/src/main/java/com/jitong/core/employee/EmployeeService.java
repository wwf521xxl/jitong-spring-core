package com.jitong.core.employee;

import com.jitong.core.audit.AuditService;
import com.jitong.core.catalog.SkillCatalog;
import com.jitong.core.catalog.SkillCatalogRepository;
import com.jitong.core.common.ApiException;
import com.jitong.core.common.Ids;
import com.jitong.core.security.PinHasher;
import com.jitong.core.security.UserPrincipal;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final EmployeeProcessSkillRepository processSkillRepository;
    private final SkillCatalogRepository skillCatalogRepository;
    private final EmployeeViewAssembler viewAssembler;
    private final PinHasher pinHasher;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Employee require(String id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("not_found", "员工不存在"));
    }

    @Transactional(readOnly = true)
    public Optional<Employee> findActiveByAccount(String account) {
        String value = account == null ? "" : account.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }
        Optional<Employee> employee;
        if (value.chars().allMatch(Character::isDigit)) {
            employee = employeeRepository.findByEmployeeNo(Integer.parseInt(value));
        } else {
            employee = employeeRepository.findById(value);
            if (employee.isEmpty()) {
                employee = employeeRepository.findByNameIgnoreCase(value);
            }
            if (employee.isEmpty() && ("admin".equalsIgnoreCase(value) || "a001".equalsIgnoreCase(value))) {
                employee = employeeRepository.findById("A001");
            }
        }
        return employee.filter(Employee::isActive);
    }

    @Transactional(readOnly = true)
    public List<Employee> activeEmployees() {
        return employeeRepository.findByActiveTrueOrderByEmployeeNoAsc();
    }

    @Transactional(readOnly = true)
    public List<EmployeeView> activeViews() {
        return viewAssembler.toViews(activeEmployees());
    }

    @Transactional(readOnly = true)
    public EmployeeView view(Employee employee) {
        return viewAssembler.toView(employee);
    }

    @Transactional
    public Employee add(UserPrincipal actor, NewEmployeeCommand command) {
        String name = command.name() == null ? "" : command.name().trim();
        String role = command.role() == null || command.role().isBlank() ? "员工" : command.role().trim();
        String pin = command.pin() == null ? "" : command.pin().trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("name_required", "请输入员工姓名");
        }
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        if (command.employeeNo() <= 0 || command.employeeNo() > 9999) {
            throw ApiException.badRequest("bad_employee_no", "工号必须在 1 到 9999 之间");
        }
        if (pin.length() != 6) {
            throw ApiException.badRequest("bad_pin", "员工 PIN 必须是 6 位");
        }
        if (employeeRepository.existsByEmployeeNo(command.employeeNo())) {
            throw ApiException.conflict("employee_no_exists", "工号已存在");
        }
        List<String> processes = cleanProcesses(command.processSkills());

        Employee employee = new Employee();
        employee.setId(Ids.employeeId());
        employee.setEmployeeNo(command.employeeNo());
        employee.setName(name);
        employee.setRole(role);
        employee.setAvatar(name.substring(0, 1));
        employee.setAdmin(false);
        employee.setCanCreateJob(command.canCreateJob());
        employee.setActive(true);
        employee.setPinHash(pinHasher.hash(pin));
        employee.setCreatedAt(Instant.now(clock));
        employeeRepository.save(employee);

        for (String process : processes) {
            EmployeeProcessSkill skill = new EmployeeProcessSkill();
            skill.setEmployeeId(employee.getId());
            skill.setProcessName(process);
            processSkillRepository.save(skill);
        }
        auditService.record(actor.id(), "add_employee", "employee", employee.getId(),
                command.employeeNo() + "/" + name + "/" + String.join(",", processes));
        return employee;
    }

    @Transactional
    public void update(UserPrincipal actor, String employeeId, String rawName, String rawRole) {
        Employee employee = require(employeeId);
        if (employee.isAdmin()) {
            throw ApiException.badRequest("admin_rename", "管理员姓名请使用厂长改名工具");
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty()) {
            throw ApiException.badRequest("name_required", "员工姓名不能为空");
        }
        if (name.length() > 40) {
            name = name.substring(0, 40);
        }
        String role = rawRole == null || rawRole.isBlank() ? employee.getRole() : rawRole.trim();
        if (role.length() > 60) {
            role = role.substring(0, 60);
        }
        String oldName = employee.getName();
        employee.setName(name);
        employee.setRole(role);
        employee.setAvatar(name.substring(0, 1));
        employee.setUpdatedAt(Instant.now(clock));
        employeeRepository.save(employee);
        auditService.record(actor.id(), "update_employee", "employee", employeeId, oldName + " -> " + name);
    }

    @Transactional
    public void deactivate(UserPrincipal actor, String employeeId) {
        Employee employee = require(employeeId);
        if (employee.isAdmin()) {
            throw ApiException.badRequest("admin_cannot_deactivate", "不能停用管理员账号");
        }
        if (!employee.isActive()) {
            return;
        }
        employee.setActive(false);
        employee.setTokenVersion(employee.getTokenVersion() + 1);
        employee.setUpdatedAt(Instant.now(clock));
        employeeRepository.save(employee);
        auditService.record(actor.id(), "deactivate_employee", "employee", employeeId, employee.getName());
    }

    @Transactional
    public void setProcessSkills(UserPrincipal actor, String employeeId, List<String> rawProcesses) {
        Employee employee = require(employeeId);
        if (employee.isAdmin()) {
            throw ApiException.badRequest("admin_skill", "管理员不能设置工序技能");
        }
        List<String> processes = cleanProcesses(rawProcesses);
        processSkillRepository.deleteByEmployeeIdBulk(employeeId);
        for (String process : processes) {
            EmployeeProcessSkill skill = new EmployeeProcessSkill();
            skill.setEmployeeId(employeeId);
            skill.setProcessName(process);
            processSkillRepository.save(skill);
        }
        auditService.record(actor.id(), "set_process_skills", "employee", employeeId, String.join("、", processes));
    }

    @Transactional
    public String resetPin(UserPrincipal actor, String employeeId) {
        Employee employee = require(employeeId);
        if (employee.isAdmin()) {
            throw ApiException.badRequest("admin_pin", "管理员密码请使用修改密码功能");
        }
        String pin = Ids.numericPin();
        employee.setPinHash(pinHasher.hash(pin));
        employee.setTokenVersion(employee.getTokenVersion() + 1);
        employee.setUpdatedAt(Instant.now(clock));
        employeeRepository.save(employee);
        auditService.record(actor.id(), "reset_pin", "employee", employeeId, "");
        return pin;
    }

    @Transactional
    public void setCreatePermission(UserPrincipal actor, String employeeId, boolean allowed) {
        Employee employee = require(employeeId);
        if (employee.isAdmin()) {
            throw ApiException.badRequest("admin_cannot_create", "管理员不能建单，也不能被授予建单权限");
        }
        if (!employee.isActive()) {
            throw ApiException.badRequest("inactive", "停用员工不能授予建单权限");
        }
        if (!allowed && employee.isCanCreateJob()
                && employeeRepository.countByActiveTrueAndAdminFalseAndCanCreateJobTrue() <= 1) {
            throw ApiException.badRequest("last_creator", "至少保留一名可建单人员");
        }
        employee.setCanCreateJob(allowed);
        employee.setUpdatedAt(Instant.now(clock));
        employeeRepository.save(employee);
        auditService.record(actor.id(), "set_create_permission", "employee", employeeId,
                allowed ? "允许建单" : "取消建单");
    }

    private List<String> cleanProcesses(List<String> rawProcesses) {
        Set<String> allowed = new LinkedHashSet<>();
        for (SkillCatalog skill : skillCatalogRepository.findAllByOrderBySortAscNameAsc()) {
            if (skill.isActive()) {
                allowed.add(skill.getName());
            }
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (rawProcesses != null) {
            for (String raw : rawProcesses) {
                String process = raw == null ? "" : raw.trim();
                if (process.isEmpty()) {
                    continue;
                }
                if (!allowed.contains(process)) {
                    throw ApiException.badRequest("unknown_process", "未知工序：" + process);
                }
                result.add(process);
            }
        }
        return List.copyOf(result);
    }
}
