package com.jitong.core.report;

import com.jitong.core.audit.AuditService;
import com.jitong.core.common.ApiException;
import com.jitong.core.common.Ids;
import com.jitong.core.common.TimeFormat;
import com.jitong.core.employee.Employee;
import com.jitong.core.employee.EmployeeRepository;
import com.jitong.core.group.GroupService;
import com.jitong.core.group.GroupView;
import com.jitong.core.job.JobService;
import com.jitong.core.job.JobStep;
import com.jitong.core.job.JobStepRepository;
import com.jitong.core.job.ProductionJob;
import com.jitong.core.photo.PhotoService;
import com.jitong.core.security.UserPrincipal;
import com.jitong.core.settings.SettingsService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportingService {

    private static final Set<String> GROUP_PROCESS_NAMES =
            Set.of("写真过板", "写真亚克力加工", "激光切割");
    private static final BigDecimal MAX_QTY = new BigDecimal("9999");

    private final JobService jobService;
    private final JobStepRepository stepRepository;
    private final PieceRecordRepository recordRepository;
    private final CorrectionRepository correctionRepository;
    private final EmployeeRepository employeeRepository;
    private final GroupService groupService;
    private final SettingsService settingsService;
    private final RecordViewAssembler viewAssembler;
    private final PhotoService photoService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public RecordView createRecord(UserPrincipal actor, RecordCreateCommand command, boolean skipUpstream) {
        String jobId = command.jobId() == null ? "" : command.jobId().trim();
        if (jobId.isEmpty()) {
            throw ApiException.badRequest("请选择订单");
        }
        if (command.qty() == null) {
            throw ApiException.badRequest("数量或工序格式不正确");
        }
        BigDecimal qty = command.qty().setScale(3, RoundingMode.HALF_UP);
        if (qty.compareTo(BigDecimal.ZERO) <= 0 || qty.compareTo(MAX_QTY) > 0) {
            throw ApiException.badRequest("数量必须在 0 到 9999 之间");
        }

        ProductionJob job = jobService.requireActiveJob(jobId);
        JobStep step = stepRepository.findByJobIdAndStepNo(jobId, command.stepNo())
                .orElseThrow(() -> ApiException.notFound("step_not_found", "工序不存在"));
        Employee employee = employeeRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.unauthorized("请先登录"));
        if (!jobService.canReport(employee, job, step)) {
            throw ApiException.forbidden("你的技能不包含该工序，请联系厂长授权");
        }
        String unit = command.unit() == null || command.unit().isBlank() ? step.getUnit() : command.unit().trim();
        if (!unit.equals(step.getUnit())) {
            throw ApiException.badRequest("wrong_unit", "计量单位与工序配置不一致");
        }

        String idempotencyKey = command.idempotencyKey() == null ? "" : command.idempotencyKey().trim();
        if (idempotencyKey.isEmpty()) {
            idempotencyKey = Ids.newIdempotencyKey();
        }
        Optional<PieceRecord> existing = recordRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return viewAssembler.toView(existing.get());
        }

        int duplicateMinutes = settingsService.duplicateMinutes();
        Instant cutoff = Instant.now(clock).minus(duplicateMinutes, ChronoUnit.MINUTES);
        Optional<PieceRecord> duplicate = recordRepository
                .findFirstByEmployeeIdAndJobIdAndStepNoAndQtyAndStatusNotAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        actor.id(), jobId, command.stepNo(), qty, RecordStatus.VOID, cutoff);
        if (duplicate.isPresent() && !command.forceDuplicate()) {
            throw ApiException.conflict("duplicate", "疑似重复报工：刚提交过相同数量，请确认后再试");
        }

        BigDecimal maxQty = skipUpstream ? null : maxReportable(job, step);
        if (maxQty != null && maxQty.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("upstream_empty", "上游尚未完成，暂时不能报工");
        }
        boolean over = maxQty != null && qty.compareTo(maxQty.add(new BigDecimal("0.0001"))) > 0;

        String note = command.note() == null ? "" : command.note().trim();
        if (note.length() > 500) {
            note = note.substring(0, 500);
        }
        if (over) {
            String prefix = "超出可报数量（可报 " + plain(maxQty) + " " + step.getUnit() + "）";
            note = (prefix + (note.isEmpty() ? "" : "；" + note));
            if (note.length() > 500) {
                note = note.substring(0, 500);
            }
        }

        PieceRecord record = new PieceRecord();
        record.setId(Ids.recordId());
        record.setJobId(jobId);
        record.setStepNo(command.stepNo());
        record.setLine(job.getLine());
        record.setEmployeeId(actor.id());
        record.setQty(qty);
        record.setUnit(step.getUnit());
        record.setStatus(over ? RecordStatus.PENDING : RecordStatus.VALID);
        record.setNote(note.isEmpty() ? null : note);
        record.setIdempotencyKey(idempotencyKey);
        record.setSource(command.source() == null || command.source().isBlank() ? "scan" : command.source().trim());
        record.setDeviceId(command.deviceId() == null ? "" : command.deviceId().trim().substring(
                0, Math.min(64, command.deviceId().trim().length())));
        record.setPhotoPath(photoService.save(record.getId(), command.photo()));
        record.setCreatedAt(TimeFormat.nowSeconds(clock));
        recordRepository.save(record);
        auditService.record(actor.id(), "create_record", "record", record.getId(),
                jobId + "/" + command.stepNo() + "/" + qty);
        return viewAssembler.toView(record);
    }

    @Transactional
    public AutoReportResult autoReport(UserPrincipal actor, String jobId, Integer stepNo) {
        ProductionJob job = jobService.requireActiveJob(jobId);
        BigDecimal qty = jobService.orderQuantity(job);
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            throw ApiException.badRequest("missing_quantity", "订单没有填写米数/数量，不能自动报工");
        }
        Employee employee = employeeRepository.findById(actor.id())
                .orElseThrow(() -> ApiException.unauthorized("请先登录"));
        List<JobStep> steps = stepRepository.findByJobIdOrderByStepNoAsc(jobId);

        JobStep chosen;
        if (stepNo != null) {
            chosen = stepRepository.findByJobIdAndStepNo(jobId, stepNo)
                    .orElseThrow(() -> ApiException.notFound("step_not_found", "工序不存在"));
            if (!jobService.canReport(employee, job, chosen)) {
                throw ApiException.forbidden("你的技能不包含该工序，请联系厂长授权");
            }
            if (recordRepository.existsByJobIdAndStepNoAndStatusNot(
                    jobId, stepNo, RecordStatus.VOID)) {
                throw ApiException.conflict("step_done", "该工序已经报工，不能重复报");
            }
        } else {
            List<Integer> options = jobService.autoStepNos(job, employee, steps);
            if (options.isEmpty()) {
                if (recordRepository.existsByJobIdAndStatusNot(jobId, RecordStatus.VOID)) {
                    throw ApiException.conflict("job_done", "订单已全部报工或等待确认");
                }
                throw ApiException.forbidden("no_step", "当前没有你可以自动报工的工序");
            }
            int chosenNo = options.get(0);
            chosen = stepRepository.findByJobIdAndStepNo(jobId, chosenNo)
                    .orElseThrow(() -> ApiException.notFound("step_not_found", "工序不存在"));
        }

        groupService.closeOpenGroupsBefore(job, chosen.getStepNo(), actor);
        String processName = chosen.getStepName() == null ? "" : chosen.getStepName();
        if (GROUP_PROCESS_NAMES.contains(processName)) {
            com.jitong.core.group.JobStepGroup group =
                    groupService.openOrJoin(job, chosen.getStepNo(), employee, qty);
            GroupView groupView = groupService.view(group);
            return new AutoReportResult(null, groupView);
        }

        RecordCreateCommand command = new RecordCreateCommand(
                jobId,
                chosen.getStepNo(),
                qty,
                job.getUnit(),
                "扫码自动报工",
                Ids.newIdempotencyKey(),
                "auto",
                "",
                null,
                false
        );
        RecordView record = createRecord(actor, command, true);
        return new AutoReportResult(record, null);
    }

    @Transactional
    public void requestCorrection(UserPrincipal actor, String recordId, CorrectionCommand command) {
        PieceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> ApiException.notFound("not_found", "记录不存在"));
        if (!record.getEmployeeId().equals(actor.id())) {
            throw ApiException.forbidden("只能申请更正自己的记录");
        }
        if (record.getStatus() != RecordStatus.VALID) {
            throw ApiException.badRequest("not_valid", "只有已计入记录可以申请冲销");
        }
        if (correctionRepository.existsByRecordIdAndStatus(recordId, CorrectionStatus.PENDING)) {
            throw ApiException.conflict("already_pending", "该记录已有待审批的冲销申请");
        }
        Correction correction = new Correction();
        correction.setId(Ids.correctionId());
        correction.setRecordId(recordId);
        correction.setReason(limit(command.reason() == null || command.reason().isBlank()
                ? "其他" : command.reason().trim(), 64));
        correction.setNote(limit(command.note() == null ? "" : command.note().trim(), 500));
        correction.setStatus(CorrectionStatus.PENDING);
        correction.setRequestedBy(actor.id());
        correction.setCreatedAt(TimeFormat.nowSeconds(clock));
        correctionRepository.save(correction);
        auditService.record(actor.id(), "request_correction", "correction", correction.getId(), recordId);
    }

    @Transactional
    public void approveRecord(UserPrincipal actor, String recordId) {
        PieceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> ApiException.notFound("not_found", "记录不存在"));
        if (record.getStatus() != RecordStatus.PENDING) {
            throw ApiException.badRequest("not_pending", "只有待确认记录可以确认");
        }
        record.setStatus(RecordStatus.VALID);
        record.setApprovedBy(actor.id());
        record.setApprovedAt(TimeFormat.nowSeconds(clock));
        recordRepository.save(record);
        auditService.record(actor.id(), "approve_record", "record", recordId, "");
    }

    @Transactional
    public void rejectRecord(UserPrincipal actor, String recordId, String reason) {
        PieceRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> ApiException.notFound("not_found", "记录不存在"));
        if (record.getStatus() != RecordStatus.PENDING) {
            throw ApiException.badRequest("not_pending", "只有待确认记录可以驳回");
        }
        record.setStatus(RecordStatus.VOID);
        record.setNote(limit((record.getNote() == null ? "" : record.getNote())
                + " · 驳回：" + (reason == null || reason.isBlank() ? "厂长驳回" : reason.trim()), 500));
        record.setApprovedBy(actor.id());
        record.setApprovedAt(TimeFormat.nowSeconds(clock));
        recordRepository.save(record);
        auditService.record(actor.id(), "reject_record", "record", recordId, reason == null ? "" : reason);
    }

    @Transactional
    public void decideCorrection(UserPrincipal actor, String correctionId, boolean approve) {
        Correction correction = correctionRepository.findById(correctionId)
                .orElseThrow(() -> ApiException.notFound("not_found", "冲销申请不存在"));
        if (correction.getStatus() != CorrectionStatus.PENDING) {
            throw ApiException.badRequest("already_decided", "该申请已处理");
        }
        correction.setStatus(approve ? CorrectionStatus.APPROVED : CorrectionStatus.REJECTED);
        correction.setApprovedBy(actor.id());
        correction.setDecidedAt(TimeFormat.nowSeconds(clock));
        correctionRepository.save(correction);
        if (approve) {
            PieceRecord record = recordRepository.findById(correction.getRecordId())
                    .orElseThrow(() -> ApiException.notFound("not_found", "记录不存在"));
            record.setStatus(RecordStatus.VOID);
            recordRepository.save(record);
        }
        auditService.record(actor.id(), approve ? "approve_correction" : "reject_correction",
                "correction", correctionId, correction.getRecordId());
    }

    @Transactional(readOnly = true)
    public BigDecimal maxReportable(ProductionJob job, JobStep step) {
        BigDecimal done = recordRepository.sumQty(
                job.getId(), step.getStepNo(), step.getUnit(), RecordStatus.VALID);
        done = done == null ? BigDecimal.ZERO : done;
        BigDecimal planned = step.getPlannedQty() == null ? BigDecimal.ZERO : step.getPlannedQty();
        BigDecimal plannedLeft = planned.compareTo(BigDecimal.ZERO) > 0
                ? max(BigDecimal.ZERO, planned.subtract(done))
                : null;
        if (step.getStepNo() == 1) {
            return round(plannedLeft);
        }
        Optional<JobStep> upstream = stepRepository.findByJobIdAndStepNo(job.getId(), step.getStepNo() - 1);
        if (upstream.isEmpty() || !upstream.get().getUnit().equals(step.getUnit())) {
            return round(plannedLeft);
        }
        BigDecimal upDone = recordRepository.sumQty(
                job.getId(), upstream.get().getStepNo(), upstream.get().getUnit(), RecordStatus.VALID);
        upDone = upDone == null ? BigDecimal.ZERO : upDone;
        BigDecimal available = max(BigDecimal.ZERO, upDone.subtract(done));
        if (plannedLeft != null) {
            available = available.min(plannedLeft);
        }
        return round(available);
    }

    private BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(3, RoundingMode.HALF_UP);
    }

    private BigDecimal max(BigDecimal left, BigDecimal right) {
        return left.compareTo(right) >= 0 ? left : right;
    }

    private String plain(BigDecimal value) {
        return value == null ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private String limit(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
