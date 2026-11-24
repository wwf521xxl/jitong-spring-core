package com.jitong.core.job;

import com.jitong.core.audit.AuditService;
import com.jitong.core.catalog.CatalogService;
import com.jitong.core.catalog.Route;
import com.jitong.core.catalog.RouteRepository;
import com.jitong.core.catalog.RouteStep;
import com.jitong.core.catalog.RouteStepRepository;
import com.jitong.core.common.ApiException;
import com.jitong.core.common.Ids;
import com.jitong.core.common.TimeFormat;
import com.jitong.core.employee.Employee;
import com.jitong.core.employee.EmployeeProcessSkillRepository;
import com.jitong.core.employee.EmployeeRepository;
import com.jitong.core.employee.EmployeeSkillRepository;
import com.jitong.core.group.GroupService;
import com.jitong.core.report.Correction;
import com.jitong.core.report.CorrectionRepository;
import com.jitong.core.report.CorrectionStatus;
import com.jitong.core.report.PieceRecord;
import com.jitong.core.report.PieceRecordRepository;
import com.jitong.core.report.RecordStatus;
import com.jitong.core.security.UserPrincipal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobService {

    private static final Set<String> UNITS = Set.of("m²", "件", "套", "块");
    private static final BigDecimal MAX_METERS = new BigDecimal("999999");

    private final ProductionJobRepository jobRepository;
    private final JobStepRepository stepRepository;
    private final RouteRepository routeRepository;
    private final RouteStepRepository routeStepRepository;
    private final PieceRecordRepository recordRepository;
    private final CorrectionRepository correctionRepository;
    private final EmployeeRepository employeeRepository;
    private final EmployeeProcessSkillRepository processSkillRepository;
    private final EmployeeSkillRepository legacySkillRepository;
    private final GroupService groupService;
    private final JobViewAssembler viewAssembler;
    private final CatalogService catalogService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public JobView createJob(UserPrincipal actor, JobCreateCommand command) {
        if (actor.admin() || !actor.canCreateJob()) {
            throw ApiException.forbidden("只有排版人员可以新建订单");
        }
        String unit = command.unit() == null ? "" : command.unit().trim();
        if (!UNITS.contains(unit)) {
            throw ApiException.badRequest("计量单位不支持");
        }
        String line = command.line() == null ? "" : command.line().trim();
        if (!"写真".equals(line) && !"亚克力".equals(line)) {
            line = "m²".equals(unit) ? "写真" : "亚克力";
        }
        String routeKey = command.routeKey() == null ? "" : command.routeKey().trim().toUpperCase();
        if (routeKey.isEmpty()) {
            routeKey = "写真".equals(line) ? "PHOTO" : "A";
        }
        Route route = routeRepository.findByRouteKeyAndActiveTrue(routeKey)
                .orElseThrow(() -> ApiException.badRequest("route_not_found", "工艺路线不存在"));
        if (!route.getLine().equals(line)) {
            throw ApiException.badRequest("route_not_found", "工艺路线与产线不匹配");
        }
        List<String> routeSteps = routeStepRepository.findByRouteIdOrderByStepNoAsc(route.getId()).stream()
                .map(RouteStep::getProcessName)
                .toList();
        if (routeSteps.isEmpty()) {
            throw ApiException.badRequest("route_empty", "该路线还没有工序");
        }

        BigDecimal meters = command.meters() == null ? BigDecimal.ZERO : command.meters();
        meters = meters.setScale(3, RoundingMode.HALF_UP);
        if (meters.compareTo(BigDecimal.ZERO) < 0 || meters.compareTo(MAX_METERS) > 0) {
            throw ApiException.badRequest("米数必须在 0 到 999999 之间");
        }
        String remark = command.remark() == null ? "" : command.remark().trim();
        if (remark.length() > 500) {
            remark = remark.substring(0, 500);
        }
        String orderNo = command.orderNo() == null ? "" : command.orderNo().trim();
        if (orderNo.length() > 64) {
            orderNo = orderNo.substring(0, 64);
        }
        if (orderNo.isEmpty()) {
            do {
                orderNo = Ids.orderNo();
            } while (jobRepository.existsByOrderNo(orderNo));
        } else if (jobRepository.existsByOrderNo(orderNo)) {
            throw ApiException.conflict("order_exists", "订单号已存在");
        }

        Instant now = TimeFormat.nowSeconds(clock);
        ProductionJob job = new ProductionJob();
        job.setId(Ids.jobId());
        job.setOrderNo(orderNo);
        job.setLine(line);
        job.setProduct("");
        job.setSpec("");
        job.setCustomer("");
        job.setPlannedQty(BigDecimal.ZERO);
        job.setMeters(meters);
        job.setUnit(unit);
        job.setPriority("正常");
        job.setRemark(remark);
        job.setRouteKey(routeKey);
        job.setStatus(JobStatus.ACTIVE);
        job.setQrToken(uniqueQrToken());
        job.setCreatedBy(actor.id());
        job.setCreatedAt(now);
        jobRepository.save(job);

        for (int index = 0; index < routeSteps.size(); index++) {
            JobStep step = new JobStep();
            step.setJobId(job.getId());
            step.setStepNo(index + 1);
            step.setStepName(routeSteps.get(index));
            step.setPlannedQty(BigDecimal.ZERO);
            step.setUnit(unit);
            stepRepository.save(step);
        }
        auditService.record(actor.id(), "create_job", "job", job.getId(),
                orderNo + "/" + unit + "/" + line + "/" + routeKey);
        return viewAssembler.assemble(job);
    }

    @Transactional
    public JobView editJob(UserPrincipal actor, String jobId, JobEditCommand command) {
        ProductionJob job = requireJob(jobId);
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw ApiException.badRequest("job_not_active", "只有进行中的订单可以修改");
        }
        String orderNo = command.orderNo() == null ? "" : command.orderNo().trim();
        if (orderNo.isEmpty()) {
            orderNo = job.getOrderNo();
        }
        if (orderNo.length() > 64) {
            orderNo = orderNo.substring(0, 64);
        }
        if (!orderNo.equals(job.getOrderNo()) && jobRepository.existsByOrderNo(orderNo)) {
            throw ApiException.conflict("order_exists", "订单号已存在");
        }
        BigDecimal currentMeters = job.getMeters() == null ? BigDecimal.ZERO : job.getMeters();
        BigDecimal meters = command.meters() == null ? currentMeters : command.meters();
        meters = meters.setScale(3, RoundingMode.HALF_UP);
        if (meters.compareTo(BigDecimal.ZERO) < 0 || meters.compareTo(MAX_METERS) > 0) {
            throw ApiException.badRequest("米数必须在 0 到 999999 之间");
        }
        long reported = recordRepository.countByJobIdAndStatusNot(jobId, RecordStatus.VOID);
        if (reported > 0 && meters.compareTo(currentMeters) != 0) {
            throw ApiException.conflict("job_has_records", "订单已有报工记录，不能修改米数；只能改订单号或备注");
        }
        String remark = command.remark() == null ? job.getRemark() : command.remark().trim();
        if (remark != null && remark.length() > 500) {
            remark = remark.substring(0, 500);
        }
        job.setOrderNo(orderNo);
        job.setMeters(meters);
        job.setRemark(remark);
        jobRepository.save(job);
        auditService.record(actor.id(), "edit_job", "job", jobId, orderNo + "/" + meters);
        return viewAssembler.assemble(job);
    }

    @Transactional
    public JobView voidJob(UserPrincipal actor, String jobId, String reason) {
        ProductionJob job = requireJob(jobId);
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw ApiException.badRequest("job_not_active", "只有进行中的订单可以作废");
        }
        String note = reason == null || reason.isBlank() ? "厂长作废" : reason.trim();
        if (note.length() > 200) {
            note = note.substring(0, 200);
        }
        job.setStatus(JobStatus.VOID);
        jobRepository.save(job);
        groupService.cancelOpenGroups(job, actor, "订单作废，自动取消合报");

        List<PieceRecord> records = recordRepository.findByJobIdAndStatusNot(jobId, RecordStatus.VOID);
        List<String> recordIds = new ArrayList<>();
        for (PieceRecord record : records) {
            record.setStatus(RecordStatus.VOID);
            record.setNote(append(record.getNote(), " · 订单作废：" + note));
            recordRepository.save(record);
            recordIds.add(record.getId());
        }
        if (!recordIds.isEmpty()) {
            for (Correction correction : correctionRepository
                    .findByRecordIdInAndStatus(recordIds, CorrectionStatus.PENDING)) {
                correction.setStatus(CorrectionStatus.REJECTED);
                correction.setNote(append(correction.getNote(), " · 订单作废"));
                correctionRepository.save(correction);
            }
        }
        auditService.record(actor.id(), "void_job", "job", jobId, note);
        return viewAssembler.assemble(job);
    }

    @Transactional
    public JobView completeJob(UserPrincipal actor, String jobId) {
        ProductionJob job = requireJob(jobId);
        if (!groupService.canManage(actor, job)) {
            throw ApiException.forbidden("只有厂长或建单人才能完成订单");
        }
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw ApiException.badRequest("not_active", "订单不是进行中");
        }
        groupService.closeAllOpenGroups(job, actor, "完成订单，自动结束合报");
        List<String> missing = new ArrayList<>();
        for (JobStep step : stepRepository.findByJobIdOrderByStepNoAsc(jobId)) {
            if (recordRepository.countByJobIdAndStepNoAndStatus(
                    jobId, step.getStepNo(), RecordStatus.VALID) == 0) {
                missing.add(String.valueOf(step.getStepNo()));
            }
        }
        if (!missing.isEmpty()) {
            throw ApiException.badRequest("steps_incomplete", "还有工序未完成：" + String.join("、", missing));
        }
        job.setStatus(JobStatus.COMPLETED);
        jobRepository.save(job);
        auditService.record(actor.id(), "complete_job", "job", jobId, "完成订单");
        return viewAssembler.assemble(job);
    }

    @Transactional(readOnly = true)
    public AutoReportOptions autoReportOptions(UserPrincipal actor, String jobId) {
        ProductionJob job = requireActiveJob(jobId);
        Employee employee = employeeRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.unauthorized("请先登录"));
        BigDecimal qty = orderQuantity(job);
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("missing_quantity", "订单没有填写米数/数量，不能自动报工");
        }
        List<JobStep> steps = stepRepository.findByJobIdOrderByStepNoAsc(jobId);
        List<Integer> options = autoStepNos(job, employee, steps);
        return new AutoReportOptions(
                job.getId(),
                job.getOrderNo(),
                job.getLine(),
                qty,
                job.getUnit(),
                options,
                steps.stream()
                        .filter(step -> options.contains(step.getStepNo()))
                        .map(step -> new AutoReportOptions.AutoStep(
                                step.getStepNo(),
                                catalogService.displayName(step.getStepName())))
                        .toList()
        );
    }

    @Transactional(readOnly = true)
    public List<Integer> autoStepNos(ProductionJob job, Employee employee, List<JobStep> steps) {
        List<Integer> result = new ArrayList<>();
        for (JobStep step : steps) {
            if (!canReport(employee, job, step)) {
                continue;
            }
            if (recordRepository.existsByJobIdAndStepNoAndStatusNot(
                    job.getId(), step.getStepNo(), RecordStatus.VOID)) {
                continue;
            }
            result.add(step.getStepNo());
        }
        return result;
    }

    @Transactional(readOnly = true)
    public boolean canReport(Employee employee, ProductionJob job, JobStep step) {
        if (employee.isAdmin()) {
            return true;
        }
        if (step.getStepName() != null && !step.getStepName().isBlank()) {
            return processSkillRepository.existsByEmployeeIdAndProcessName(
                    employee.getId(), step.getStepName());
        }
        return legacySkillRepository.findByEmployeeIdOrderByLineAscStepNoAsc(employee.getId()).stream()
                .anyMatch(skill -> skill.getLine().equals(job.getLine())
                        && skill.getStepNo() == step.getStepNo());
    }

    @Transactional(readOnly = true)
    public ProductionJob requireJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("job_not_found", "订单不存在"));
    }

    @Transactional(readOnly = true)
    public ProductionJob requireActiveJob(String jobId) {
        ProductionJob job = requireJob(jobId);
        if (job.getStatus() != JobStatus.ACTIVE) {
            throw ApiException.notFound("job_not_found", "订单不存在或已完成");
        }
        return job;
    }

    @Transactional(readOnly = true)
    public BigDecimal orderQuantity(ProductionJob job) {
        if (job.getMeters() != null && job.getMeters().compareTo(BigDecimal.ZERO) > 0) {
            return job.getMeters();
        }
        return job.getPlannedQty() == null ? BigDecimal.ZERO : job.getPlannedQty();
    }

    @Transactional(readOnly = true)
    public JobView view(ProductionJob job) {
        return viewAssembler.assemble(job);
    }

    private String uniqueQrToken() {
        String token;
        do {
            token = Ids.qrToken();
        } while (jobRepository.existsByQrToken(token));
        return token;
    }

    private String append(String current, String suffix) {
        String result = (current == null ? "" : current) + suffix;
        return result.length() > 500 ? result.substring(0, 500) : result;
    }
}
