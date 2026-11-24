package com.jitong.core.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jitong.core.group.JobStepGroupEvent;
import com.jitong.core.group.JobStepGroupEventRepository;
import com.jitong.core.report.PieceRecord;
import com.jitong.core.report.PieceRecordRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PieceRecordRepository recordRepository;

    @Autowired
    private JobStepGroupEventRepository eventRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    @DisplayName("登录后 bootstrap 保持现有前端的 camelCase 契约")
    void bootstrapKeepsFrontendContract() throws Exception {
        Cookie cookie = login("7", "123456");

        mockMvc.perform(get("/api/bootstrap").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("E007"))
                .andExpect(jsonPath("$.user.isAdmin").value(false))
                .andExpect(jsonPath("$.user.canCreateJob").value(true))
                .andExpect(jsonPath("$.user.processSkills").isArray())
                .andExpect(jsonPath("$.settings.duplicateMinutes").value(10))
                .andExpect(jsonPath("$.routes[0].steps").isArray())
                .andExpect(jsonPath("$.jobs[0].steps").isArray());

        mockMvc.perform(get("/api/bootstrap"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail.code").value("unauthorized"));
    }

    @Test
    @DisplayName("管理员不能建单，排版人员可以建单且产线由单位推断")
    void createJobPermissionAndLineInference() throws Exception {
        Cookie admin = login("admin", "Admin@123456");
        mockMvc.perform(post("/api/jobs")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "unit", "件",
                                "orderNo", uniqueOrder(),
                                "remark", "管理员建单"))))
                .andExpect(status().isForbidden());

        Cookie typesetter = login("7", "123456");
        MvcResult created = mockMvc.perform(post("/api/jobs")
                        .cookie(typesetter)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "unit", "m²",
                                "orderNo", uniqueOrder(),
                                "remark", "排版建单",
                                "meters", 10))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.line").value("写真"))
                .andExpect(jsonPath("$.job.routeKey").value("PHOTO"))
                .andExpect(jsonPath("$.job.plannedQty").value(0))
                .andExpect(jsonPath("$.job.steps.length()").value(6))
                .andReturn();

        assertThat(created.getResponse().getContentAsString()).contains("写真标准 6 道");
    }

    @Test
    @DisplayName("自动报工开组、第二人加入、结束合报按人数平分")
    void autoReportAndGroupSettlement() throws Exception {
        Cookie owner = login("7", "123456");
        JsonNode job = createJob(owner, "m²", uniqueOrder(), 10, null);
        String jobId = job.get("id").asText();

        JsonNode first = autoReport(owner, jobId, null);
        assertThat(first.get("record").get("stepNo").asInt()).isEqualTo(1);
        assertThat(first.get("record").get("qty").asDouble()).isEqualTo(10.0);

        JsonNode second = autoReport(owner, jobId, null);
        assertThat(second.get("record").get("stepNo").asInt()).isEqualTo(2);

        JsonNode third = autoReport(owner, jobId, null);
        assertThat(third.get("group").get("status").asText()).isEqualTo("open");
        assertThat(third.get("group").get("memberCount").asInt()).isEqualTo(1);

        Cookie colleague = login("9", "123456");
        JsonNode joined = autoReport(colleague, jobId, null);
        assertThat(joined.get("group").get("memberCount").asInt()).isEqualTo(2);

        Cookie admin = login("admin", "Admin@123456");
        MvcResult closed = mockMvc.perform(post("/api/admin/jobs/{jobId}/groups/3/close", jobId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "测试结束"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.steps[2].group.status").value("closed"))
                .andExpect(jsonPath("$.job.steps[2].group.memberCount").value(2))
                .andExpect(jsonPath("$.job.steps[2].group.perQty").value(5.0))
                .andReturn();

        JsonNode group = objectMapper.readTree(closed.getResponse().getContentAsString())
                .get("job").get("steps").get(2).get("group");
        assertThat(group.get("members")).hasSize(2);

        List<PieceRecord> records = recordRepository.findByGroupIdOrderByEmployeeIdAsc(group.get("id").asText());
        assertThat(records).hasSize(2);
        assertThat(records.stream().map(PieceRecord::getQty).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("10.000");
        assertThat(records).allMatch(record -> "group".equals(record.getSource()));

        List<JobStepGroupEvent> events =
                eventRepository.findByGroupIdOrderByCreatedAtAsc(group.get("id").asText());
        assertThat(events).hasSize(5);
        assertThat(events.get(events.size() - 1).getToState().name()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("幂等键重复返回同一条记录，相同数量再次提交被拦截")
    void idempotencyAndDuplicateProtection() throws Exception {
        Cookie worker = login("7", "123456");
        String jobId = createJob(worker, "件", uniqueOrder(), 5, "A").get("id").asText();
        String key = "IT-" + UUID.randomUUID();

        JsonNode first = createRecord(worker, jobId, 1, 5, key, false);
        String recordId = first.get("record").get("id").asText();

        JsonNode replay = createRecord(worker, jobId, 1, 5, key, false);
        assertThat(replay.get("record").get("id").asText()).isEqualTo(recordId);

        mockMvc.perform(post("/api/records")
                        .cookie(worker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(recordBody(jobId, 1, 5, "IT-" + UUID.randomUUID(), false))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail.code").value("duplicate"));

        JsonNode forced = createRecord(worker, jobId, 1, 5, "IT-" + UUID.randomUUID(), true);
        assertThat(forced.get("record").get("status").asText()).isEqualTo("valid");
    }

    @Test
    @DisplayName("上游未完成时不能报下一道，完成后可报")
    void upstreamQuantityIsEnforced() throws Exception {
        Cookie worker = login("7", "123456");
        String jobId = createJob(worker, "件", uniqueOrder(), 5, "B").get("id").asText();

        mockMvc.perform(post("/api/records")
                        .cookie(worker)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(recordBody(jobId, 2, 2, "UP-" + UUID.randomUUID(), false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.code").value("upstream_empty"));

        createRecord(worker, jobId, 1, 5, "UP-" + UUID.randomUUID(), false);
        JsonNode second = createRecord(worker, jobId, 2, 2, "UP-" + UUID.randomUUID(), false);
        assertThat(second.get("record").get("stepNo").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("JWT Bearer 与 Cookie 都可认证；退出后旧 token 失效")
    void jwtBearerCookieAndLogout() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", "7", "pin", "123456"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = loginResult.getResponse().getCookie("jitong_session");
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("token").asText();
        assertThat(cookie).isNotNull();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/bootstrap").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("E007"));

        mockMvc.perform(post("/api/logout").cookie(cookie))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/bootstrap").cookie(cookie))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/bootstrap").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("管理员可以授予建单权限，授权后员工可建单")
    void adminCanGrantCreatePermission() throws Exception {
        Cookie admin = login("admin", "Admin@123456");
        MvcResult granted = mockMvc.perform(post("/api/admin/employees/E004/create-permission")
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("canCreateJob", true))))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode employees = objectMapper.readTree(granted.getResponse().getContentAsString())
                .get("employees");
        JsonNode e004 = null;
        for (JsonNode employee : employees) {
            if ("E004".equals(employee.get("id").asText())) {
                e004 = employee;
                break;
            }
        }
        assertThat(e004).isNotNull();
        assertThat(e004.get("canCreateJob").asBoolean()).isTrue();

        Cookie employee = login("4", "123456");
        mockMvc.perform(post("/api/jobs")
                        .cookie(employee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("unit", "件", "orderNo", uniqueOrder()))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("完成订单会结算未结束合报，并检查每道工序都有有效报工")
    void completeJobSettlesOpenGroupsAndChecksAllSteps() throws Exception {
        Cookie worker = login("7", "123456");
        String jobId = createJob(worker, "m²", uniqueOrder(), 10, null).get("id").asText();

        mockMvc.perform(post("/api/admin/jobs/{jobId}/complete", jobId).cookie(worker))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail.code").value("steps_incomplete"));

        for (int step = 1; step <= 6; step++) {
            autoReport(worker, jobId, step);
        }

        mockMvc.perform(post("/api/admin/jobs/{jobId}/complete", jobId).cookie(worker))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("completed"));
    }

    @Test
    @DisplayName("修改密码后当前设备自动续期，旧密码失效，新密码可登录")
    void changePasswordKeepsCurrentSession() throws Exception {
        Cookie oldCookie = login("7", "123456");
        MvcResult changed = mockMvc.perform(post("/api/change-password")
                        .cookie(oldCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("old", "123456", "new", "654321"))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie newCookie = changed.getResponse().getCookie("jitong_session");
        assertThat(newCookie).isNotNull();

        mockMvc.perform(get("/api/bootstrap").cookie(newCookie))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/refresh").cookie(newCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        login("7", "654321");
        mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", "7", "pin", "123456"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("超过计划量落为 pending，厂长确认后转为 valid")
    void overPlanRecordNeedsApproval() throws Exception {
        Cookie worker = login("7", "123456");
        String jobId = createJob(worker, "件", uniqueOrder(), 5, "A").get("id").asText();
        jdbcTemplate.update("update job_steps set planned_qty = 5 where job_id = ?", jobId);
        jdbcTemplate.update("update production_jobs set planned_qty = 5 where id = ?", jobId);
        entityManager.clear();

        JsonNode created = createRecord(worker, jobId, 1, 6, "OVER-" + UUID.randomUUID(), false);
        JsonNode record = created.get("record");
        assertThat(record.get("status").asText()).isEqualTo("pending");
        assertThat(record.get("note").asText()).contains("超出可报数量");

        Cookie admin = login("admin", "Admin@123456");
        mockMvc.perform(post("/api/admin/records/{recordId}/approve", record.get("id").asText())
                        .cookie(admin))
                .andExpect(status().isOk());

        assertThat(recordRepository.findById(record.get("id").asText()).orElseThrow().getStatus().wireValue())
                .isEqualTo("valid");
    }

    @Test
    @DisplayName("作废订单会把未结束的合报置为 cancelled")
    void voidJobCancelsOpenGroups() throws Exception {
        Cookie worker = login("7", "123456");
        String jobId = createJob(worker, "m²", uniqueOrder(), 10, null).get("id").asText();
        autoReport(worker, jobId, 1);
        autoReport(worker, jobId, 2);
        autoReport(worker, jobId, 3);

        Cookie admin = login("admin", "Admin@123456");
        mockMvc.perform(post("/api/admin/jobs/{jobId}/void", jobId)
                        .cookie(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("reason", "客户取消"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.job.status").value("void"))
                .andExpect(jsonPath("$.job.steps[2].group.status").value("cancelled"));
    }

    private Cookie login(String account, String pin) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("account", account, "pin", pin))))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = result.getResponse().getCookie("jitong_session");
        assertThat(cookie).as("login cookie").isNotNull();
        return cookie;
    }

    private JsonNode createJob(Cookie cookie, String unit, String orderNo, int meters, String routeKey)
            throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unit", unit);
        body.put("orderNo", orderNo);
        body.put("meters", meters);
        if (routeKey != null) {
            body.put("routeKey", routeKey);
        }
        MvcResult result = mockMvc.perform(post("/api/jobs")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("job");
    }

    private JsonNode autoReport(Cookie cookie, String jobId, Integer stepNo) throws Exception {
        String body = stepNo == null ? "{}" : json(Map.of("stepNo", stepNo));
        MvcResult result = mockMvc.perform(post("/api/jobs/{jobId}/auto-report", jobId)
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createRecord(Cookie cookie, String jobId, int stepNo, int qty, String key, boolean force)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/api/records")
                        .cookie(cookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(recordBody(jobId, stepNo, qty, key, force))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private Map<String, Object> recordBody(String jobId, int stepNo, int qty, String key, boolean force) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("jobId", jobId);
        body.put("stepNo", stepNo);
        body.put("qty", qty);
        body.put("unit", "件");
        body.put("idempotencyKey", key);
        body.put("forceDuplicate", force);
        return body;
    }

    private String uniqueOrder() {
        return "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
