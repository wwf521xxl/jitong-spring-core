package com.jitong.core.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitong.core.audit.AuditService;
import com.jitong.core.common.ApiException;
import com.jitong.core.common.StoragePaths;
import com.jitong.core.employee.Employee;
import com.jitong.core.employee.EmployeeRepository;
import com.jitong.core.security.PinHasher;
import com.jitong.core.security.UserPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClearDataService {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final List<String> BACKUP_TABLES = List.of(
            "production_jobs", "job_steps", "job_step_groups", "job_step_group_members",
            "job_step_group_events", "piece_records", "corrections", "audit_log"
    );

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final StoragePaths storagePaths;
    private final EmployeeRepository employeeRepository;
    private final PinHasher pinHasher;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public String clear(UserPrincipal actor, String password, String confirm) {
        Employee admin = employeeRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.unauthorized("请先登录"));
        if (!pinHasher.matches(password == null ? "" : password, admin.getPinHash())) {
            throw new ApiException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "wrong_password", "管理员密码不正确");
        }
        if (confirm == null || !"清空".equals(confirm.trim())) {
            throw ApiException.badRequest("confirm_required", "确认文字不对，请输入：清空");
        }
        String filename = "jitong-before-clear-" + LocalDateTime.now(clock).format(STAMP) + ".json";
        Path backup = storagePaths.getBackupsDir().resolve(filename);
        Map<String, Object> snapshot = new LinkedHashMap<>();
        for (String table : BACKUP_TABLES) {
            snapshot.put(table, jdbcTemplate.queryForList("select * from " + table));
        }
        try {
            Files.write(backup, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(snapshot));
        } catch (Exception ex) {
            throw new IllegalStateException("清空前备份失败，已中止", ex);
        }

        jdbcTemplate.update("delete from corrections");
        jdbcTemplate.update("delete from piece_records");
        jdbcTemplate.update("delete from job_step_group_events");
        jdbcTemplate.update("delete from job_step_group_members");
        jdbcTemplate.update("delete from job_step_groups");
        jdbcTemplate.update("delete from job_steps");
        jdbcTemplate.update("delete from production_jobs");
        jdbcTemplate.update("delete from audit_log");
        auditService.record(actor.id(), "clear_production_data", "system", "", "backup=" + filename);
        return filename;
    }
}
