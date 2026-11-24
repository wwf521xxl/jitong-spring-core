package com.jitong.core.seed;

import com.jitong.core.common.TimeFormat;
import com.jitong.core.config.JitongProperties;
import com.jitong.core.security.PinHasher;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 演示数据：17 名员工 + 厂长、7 条路线、4 个订单、合报组、待审批冲销。
 * 姓名、订单号均为演示数据，不含生产库内容。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DemoDataSeeder implements ApplicationRunner {

    private static final List<String> SKILLS = List.of(
            "排版", "写真打印", "写真过板", "写真覆膜", "裁切", "写真亚克力加工",
            "特殊亚克力排版", "亚克力排版", "打印", "过胶", "UV雪弗板覆膜", "覆膜",
            "激光切割", "UV雪弗板裁切", "雪弗板切割", "特殊亚克力加工"
    );

    private static final Map<String, List<Integer>> PROCESS_SKILLS = new LinkedHashMap<>();

    static {
        PROCESS_SKILLS.put("排版", List.of(5, 7, 16));
        PROCESS_SKILLS.put("写真打印", List.of(13, 16));
        PROCESS_SKILLS.put("写真过板", List.of(1, 2, 3, 9, 10));
        PROCESS_SKILLS.put("写真覆膜", List.of(9));
        PROCESS_SKILLS.put("裁切", List.of(4, 6, 5, 7, 9, 17));
        PROCESS_SKILLS.put("写真亚克力加工", List.of(1, 5, 7, 14, 15));
        PROCESS_SKILLS.put("特殊亚克力排版", List.of(5, 7, 11, 12));
        PROCESS_SKILLS.put("亚克力排版", List.of(5, 7, 11, 12));
        PROCESS_SKILLS.put("打印", List.of(8));
        PROCESS_SKILLS.put("过胶", List.of(9, 10));
        PROCESS_SKILLS.put("UV雪弗板覆膜", List.of(1, 5, 7, 9, 2, 3));
        PROCESS_SKILLS.put("覆膜", List.of(1, 5, 7, 9, 2, 3));
        PROCESS_SKILLS.put("激光切割", List.of(4, 6, 17));
        PROCESS_SKILLS.put("UV雪弗板裁切", List.of(5, 7, 9, 11, 12));
        PROCESS_SKILLS.put("雪弗板切割", List.of(5, 7, 9, 11, 12));
        PROCESS_SKILLS.put("特殊亚克力加工", List.of(1, 5, 7, 14, 15));
    }

    private static final List<EmployeeSeed> EMPLOYEES = List.of(
            new EmployeeSeed("E001", 1, "员工01", "综合岗位", List.of("写真:5", "亚克力:5")),
            new EmployeeSeed("E002", 2, "员工02", "综合岗位", List.of("写真:5", "亚克力:5")),
            new EmployeeSeed("E003", 3, "员工03", "综合岗位", List.of("写真:5", "亚克力:5")),
            new EmployeeSeed("E004", 4, "员工04", "裁切（激光切割）", List.of("亚克力:4")),
            new EmployeeSeed("E005", 5, "员工05", "多能工（全线）", List.of(
                    "写真:1", "写真:2", "写真:3", "写真:4", "写真:5",
                    "亚克力:1", "亚克力:2", "亚克力:3", "亚克力:4", "亚克力:5")),
            new EmployeeSeed("E006", 6, "员工06", "裁切（激光切割）", List.of("写真:4", "亚克力:4")),
            new EmployeeSeed("E007", 7, "员工07", "多能工（全线）", List.of(
                    "写真:1", "写真:2", "写真:3", "写真:4", "写真:5",
                    "亚克力:1", "亚克力:2", "亚克力:3", "亚克力:4", "亚克力:5")),
            new EmployeeSeed("E008", 8, "员工08", "UV打印", List.of("亚克力:2")),
            new EmployeeSeed("E009", 9, "员工09", "过膜过板", List.of("写真:3", "亚克力:3")),
            new EmployeeSeed("E010", 10, "员工10", "过膜过板", List.of("写真:3", "亚克力:3")),
            new EmployeeSeed("E011", 11, "员工11", "亚克力排图", List.of("亚克力:1")),
            new EmployeeSeed("E012", 12, "员工12", "亚克力排图", List.of("亚克力:1")),
            new EmployeeSeed("E013", 13, "员工13", "写真/异型（打印）", List.of("写真:2")),
            new EmployeeSeed("E014", 14, "员工14", "综合岗位", List.of("写真:5", "亚克力:5")),
            new EmployeeSeed("E015", 15, "员工15", "综合岗位", List.of("写真:5", "亚克力:5")),
            new EmployeeSeed("E016", 16, "员工16", "写真/异型（排版）", List.of("写真:1")),
            new EmployeeSeed("E017", 17, "员工17", "裁切（激光切割）", List.of("写真:4", "亚克力:4"))
    );

    private final JdbcTemplate jdbcTemplate;
    private final JitongProperties properties;
    private final PinHasher pinHasher;
    private final Clock clock;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isDemo()) {
            return;
        }
        Integer count = jdbcTemplate.queryForObject("select count(*) from employees", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        log.info("初始化计件通演示数据（非生产数据）");
        seedSkills();
        seedRoutes();
        seedEmployees();
        seedJobs();
        seedSettings();
        log.info("演示账号：admin / {}，员工 7 / {}", properties.getSeed().getAdminPassword(),
                properties.getSeed().getEmployeePin());
    }

    private void seedSkills() {
        Instant now = TimeFormat.nowSeconds(clock);
        for (int index = 0; index < SKILLS.size(); index++) {
            jdbcTemplate.update("""
                    insert into skill_catalog(name, display_name, active, sort, created_at)
                    values (?, ?, true, ?, ?)
                    """, SKILLS.get(index), SKILLS.get(index), index, ts(now));
        }
    }

    private void seedRoutes() {
        Instant now = TimeFormat.nowSeconds(clock);
        route("PHOTO", "写真", "写真标准 6 道", "RT-PHOTO", 0, now,
                "排版", "写真打印", "写真过板", "写真覆膜", "裁切", "写真亚克力加工");
        route("A", "亚克力", "亚克力 A", "RT-A", 1, now,
                "特殊亚克力排版", "过胶", "激光切割", "特殊亚克力加工");
        route("B", "亚克力", "亚克力 B", "RT-B", 2, now,
                "特殊亚克力排版", "激光切割", "特殊亚克力加工");
        route("C", "亚克力", "亚克力 C", "RT-C", 3, now,
                "特殊亚克力排版", "打印", "过胶", "激光切割", "特殊亚克力加工");
        route("D", "亚克力", "亚克力 D", "RT-D", 4, now,
                "亚克力排版", "打印", "过胶", "激光切割");
        route("E", "亚克力", "亚克力 E", "RT-E", 5, now,
                "亚克力排版", "打印", "雪弗板切割");
        route("F", "亚克力", "亚克力 F", "RT-F", 6, now,
                "亚克力排版", "打印", "覆膜", "雪弗板切割", "写真亚克力加工");
    }

    private void route(String key, String line, String name, String id, int sort, Instant now,
                       String... steps) {
        jdbcTemplate.update("""
                insert into routes(id, route_key, line, name, active, sort, created_at)
                values (?, ?, ?, ?, true, ?, ?)
                """, id, key, line, name, sort, ts(now));
        for (int index = 0; index < steps.length; index++) {
            jdbcTemplate.update("""
                    insert into route_steps(route_id, step_no, process_name)
                    values (?, ?, ?)
                    """, id, index + 1, steps[index]);
        }
    }

    private void seedEmployees() {
        Instant now = TimeFormat.nowSeconds(clock);
        String employeePin = properties.getSeed().getEmployeePin();
        String adminPin = properties.getSeed().getAdminPassword();
        for (EmployeeSeed seed : EMPLOYEES) {
            boolean creator = List.of(5, 7, 11, 12, 16).contains(seed.employeeNo());
            jdbcTemplate.update("""
                    insert into employees(id, employee_no, name, role, avatar, is_admin,
                        can_create_job, active, pin_hash, token_version, created_at)
                    values (?, ?, ?, ?, ?, false, ?, true, ?, 0, ?)
                    """, seed.id(), seed.employeeNo(), seed.name(), seed.role(),
                    seed.name().substring(0, 1), creator, pinHasher.hash(employeePin), ts(now));
            for (String legacy : seed.legacySkills()) {
                String[] parts = legacy.split(":");
                jdbcTemplate.update("""
                        insert into employee_skills(employee_id, line, step_no) values (?, ?, ?)
                        """, seed.id(), parts[0], Integer.parseInt(parts[1]));
            }
            for (Map.Entry<String, List<Integer>> entry : PROCESS_SKILLS.entrySet()) {
                boolean allRounder = seed.employeeNo() == 5 || seed.employeeNo() == 7;
                if (allRounder || entry.getValue().contains(seed.employeeNo())) {
                    jdbcTemplate.update("""
                            insert into employee_process_skills(employee_id, process_name)
                            values (?, ?)
                            """, seed.id(), entry.getKey());
                }
            }
        }
        jdbcTemplate.update("""
                insert into employees(id, employee_no, name, role, avatar, is_admin,
                    can_create_job, active, pin_hash, token_version, created_at)
                values ('A001', 0, '厂长', '厂长 · 管理员', '李', true, false, true, ?, 0, ?)
                """, pinHasher.hash(adminPin), ts(now));
        for (String line : List.of("写真", "亚克力")) {
            for (int step = 1; step <= 5; step++) {
                jdbcTemplate.update("""
                        insert into employee_skills(employee_id, line, step_no) values ('A001', ?, ?)
                        """, line, step);
            }
        }
    }

    private void seedJobs() {
        Instant now = TimeFormat.nowSeconds(clock);
        Instant photoCreated = now.minus(2, ChronoUnit.HOURS);
        Instant acrylicCreated = now.minus(1, ChronoUnit.DAYS);
        Instant doneCreated = now.minus(4, ChronoUnit.DAYS);
        Instant voidCreated = now.minus(5, ChronoUnit.DAYS);

        job("JDEMO0001", "TB20261124001", "写真", "m²", new BigDecimal("48.5"),
                "PHOTO", "active", "E007", photoCreated, "QJ-DEMO-PHOTO-01");
        steps("JDEMO0001", new String[]{
                "排版", "写真打印", "写真过板", "写真覆膜", "裁切", "写真亚克力加工"}, "m²");

        job("JDEMO0002", "TB20261124002", "亚克力", "件", new BigDecimal("300"),
                "A", "active", "E011", acrylicCreated, "QJ-DEMO-ACRYLIC-01");
        steps("JDEMO0002", new String[]{
                "特殊亚克力排版", "过胶", "激光切割", "特殊亚克力加工"}, "件");

        job("JDEMO0003", "TB20261122003", "写真", "m²", new BigDecimal("26"),
                "PHOTO", "completed", "E007", doneCreated, "QJ-DEMO-DONE-01");
        steps("JDEMO0003", new String[]{
                "排版", "写真打印", "写真过板", "写真覆膜", "裁切", "写真亚克力加工"}, "m²");

        job("JDEMO0004", "TB20261121004", "亚克力", "件", new BigDecimal("50"),
                "B", "void", "E011", voidCreated, "QJ-DEMO-VOID-01");
        steps("JDEMO0004", new String[]{
                "特殊亚克力排版", "激光切割", "特殊亚克力加工"}, "件");

        record("RDEMO1001", "JDEMO0001", 1, "写真", "E007", "48.5", "m²", "valid",
                "扫码自动报工", "DEMO-J1-S1", "auto", null, null,
                photoCreated.plus(10, ChronoUnit.MINUTES));
        record("RDEMO1002", "JDEMO0001", 2, "写真", "E013", "48.5", "m²", "valid",
                "扫码自动报工", "DEMO-J1-S2", "auto", null, null,
                photoCreated.plus(25, ChronoUnit.MINUTES));

        record("RDEMO2001", "JDEMO0002", 1, "亚克力", "E011", "300", "件", "valid",
                "扫码自动报工", "DEMO-J2-S1", "auto", null, null,
                acrylicCreated.plus(1, ChronoUnit.HOURS));
        record("RDEMO2002", "JDEMO0002", 2, "亚克力", "E009", "150", "件", "valid",
                "扫码报工", "DEMO-J2-S2A", "scan", null, null,
                acrylicCreated.plus(2, ChronoUnit.HOURS));
        record("RDEMO2003", "JDEMO0002", 2, "亚克力", "E010", "150", "件", "valid",
                "扫码报工", "DEMO-J2-S2B", "scan", null, null,
                acrylicCreated.plus(2, ChronoUnit.HOURS));

        group("GDEMO2001", "JDEMO0002", 3, "closed", "300", "件", "E004",
                acrylicCreated.plus(3, ChronoUnit.HOURS), "A001",
                acrylicCreated.plus(5, ChronoUnit.HOURS), "下一道工序开始，自动结束");
        member("GDEMO2001", "E004", acrylicCreated.plus(3, ChronoUnit.HOURS), "RDEMO2004");
        member("GDEMO2001", "E006", acrylicCreated.plus(3, ChronoUnit.HOURS), "RDEMO2005");
        member("GDEMO2001", "E017", acrylicCreated.plus(3, ChronoUnit.HOURS), "RDEMO2006");
        for (int index = 0; index < 3; index++) {
            String[] ids = {"RDEMO2004", "RDEMO2005", "RDEMO2006"};
            String[] employees = {"E004", "E006", "E017"};
            record(ids[index], "JDEMO0002", 3, "亚克力", employees[index], "100", "件", "valid",
                    "合报平分：下一道工序开始，自动结束", "GROUP-GDEMO2001-" + employees[index],
                    "group", "GDEMO2001", null, acrylicCreated.plus(5, ChronoUnit.HOURS));
        }
        event("GEDEMO2001", "GDEMO2001", "open", "open", "OPEN", "E004", "开组",
                acrylicCreated.plus(3, ChronoUnit.HOURS));
        event("GEDEMO2002", "GDEMO2001", "open", "open", "JOIN", "E006", "加入合报",
                acrylicCreated.plus(3, ChronoUnit.HOURS));
        event("GEDEMO2003", "GDEMO2001", "open", "open", "JOIN", "E017", "加入合报",
                acrylicCreated.plus(3, ChronoUnit.HOURS));
        event("GEDEMO2004", "GDEMO2001", "open", "settling", "BEGIN_SETTLE", "A001", "下一道工序开始",
                acrylicCreated.plus(5, ChronoUnit.HOURS));
        event("GEDEMO2005", "GDEMO2001", "settling", "closed", "SETTLED", "A001", "下一道工序开始",
                acrylicCreated.plus(5, ChronoUnit.HOURS));

        record("RDEMO4001", "JDEMO0004", 1, "亚克力", "E011", "50", "件", "void",
                "订单作废：客户取消", "DEMO-J4-S1", "scan", null, null,
                voidCreated.plus(10, ChronoUnit.MINUTES));

        for (int step = 1; step <= 6; step++) {
            record("RDEMO30" + step, "JDEMO0003", step, "写真", "E007", "26", "m²", "valid",
                    "历史订单报工", "DEMO-J3-S" + step, "auto", null, null,
                    doneCreated.plus(step, ChronoUnit.HOURS));
        }

        group("GDEMO1001", "JDEMO0001", 3, "open", "48.5", "m²", "E009",
                photoCreated.plus(40, ChronoUnit.MINUTES), null, null, null);
        member("GDEMO1001", "E009", photoCreated.plus(40, ChronoUnit.MINUTES), null);
        member("GDEMO1001", "E010", photoCreated.plus(45, ChronoUnit.MINUTES), null);
        event("GEDEMO1001", "GDEMO1001", "open", "open", "OPEN", "E009", "开组",
                photoCreated.plus(40, ChronoUnit.MINUTES));
        event("GEDEMO1002", "GDEMO1001", "open", "open", "JOIN", "E010", "加入合报",
                photoCreated.plus(45, ChronoUnit.MINUTES));

        jdbcTemplate.update("""
                insert into corrections(id, record_id, reason, note, status, requested_by, created_at)
                values ('CDEMO1001', 'RDEMO1002', '数量录错', '实际为 47.5 m²', 'pending', 'E013', ?)
                """, ts(photoCreated.plus(50, ChronoUnit.MINUTES)));
    }

    private void seedSettings() {
        jdbcTemplate.update("insert into settings(setting_key, setting_value) values ('duplicate_minutes', '10')");
        jdbcTemplate.update("insert into settings(setting_key, setting_value) values ('tolerance', '0.05')");
        Instant now = TimeFormat.nowSeconds(clock);
        jdbcTemplate.update("""
                insert into audit_log(actor_id, action, entity_type, entity_id, detail, created_at)
                values ('system', 'seed', 'system', 'init', '初始化演示数据', ?)
                """, ts(now));
    }

    private void job(
            String id, String orderNo, String line, String unit, BigDecimal meters,
            String routeKey, String status, String createdBy, Instant createdAt, String qrToken
    ) {
        jdbcTemplate.update("""
                insert into production_jobs(id, order_no, line, product, spec, customer,
                    planned_qty, meters, unit, priority, remark, route_key, status, qr_token,
                    created_by, created_at, version)
                values (?, ?, ?, '', '', '', 0, ?, ?, '正常', '演示订单', ?, ?, ?, ?, ?, 0)
                """, id, orderNo, line, meters, unit, routeKey, status, qrToken, createdBy, ts(createdAt));
    }

    private void steps(String jobId, String[] names, String unit) {
        for (int index = 0; index < names.length; index++) {
            jdbcTemplate.update("""
                    insert into job_steps(job_id, step_no, step_name, planned_qty, unit)
                    values (?, ?, ?, 0, ?)
                    """, jobId, index + 1, names[index], unit);
        }
    }

    private void record(
            String id, String jobId, int stepNo, String line, String employeeId, String qty,
            String unit, String status, String note, String idempotencyKey, String source,
            String groupId, String photoPath, Instant createdAt
    ) {
        jdbcTemplate.update("""
                insert into piece_records(id, job_id, step_no, line, employee_id, qty, unit,
                    status, note, idempotency_key, source, photo_path, device_id, group_id,
                    created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '', ?, ?)
                """, id, jobId, stepNo, line, employeeId, new BigDecimal(qty), unit, status, note,
                idempotencyKey, source, photoPath, groupId, ts(createdAt));
    }

    private void group(
            String id, String jobId, int stepNo, String status, String totalQty, String unit,
            String createdBy, Instant createdAt, String closedBy, Instant closedAt, String reason
    ) {
        jdbcTemplate.update("""
                insert into job_step_groups(id, job_id, step_no, status, total_qty, unit,
                    created_by, created_at, closed_by, closed_at, close_reason, version)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, jobId, stepNo, status, new BigDecimal(totalQty), unit, createdBy,
                ts(createdAt), closedBy, closedAt == null ? null : ts(closedAt), reason);
    }

    private void member(String groupId, String employeeId, Instant joinedAt, String recordId) {
        jdbcTemplate.update("""
                insert into job_step_group_members(group_id, employee_id, joined_at, record_id)
                values (?, ?, ?, ?)
                """, groupId, employeeId, ts(joinedAt), recordId);
    }

    private void event(
            String id, String groupId, String from, String to, String action,
            String actorId, String reason, Instant createdAt
    ) {
        jdbcTemplate.update("""
                insert into job_step_group_events(id, group_id, from_state, to_state, action,
                    actor_id, reason, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?)
                """, id, groupId, from, to, action, actorId, reason, ts(createdAt));
    }

    private OffsetDateTime ts(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private record EmployeeSeed(
            String id, int employeeNo, String name, String role, List<String> legacySkills
    ) {
    }
}
