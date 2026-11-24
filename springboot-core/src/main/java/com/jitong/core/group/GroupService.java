package com.jitong.core.group;

import com.jitong.core.audit.AuditService;
import com.jitong.core.common.ApiException;
import com.jitong.core.common.Ids;
import com.jitong.core.employee.Employee;
import com.jitong.core.job.ProductionJob;
import com.jitong.core.job.ProductionJobRepository;
import com.jitong.core.report.PieceRecord;
import com.jitong.core.report.PieceRecordRepository;
import com.jitong.core.report.RecordStatus;
import com.jitong.core.security.UserPrincipal;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GroupService {

    private final JobStepGroupRepository groupRepository;
    private final JobStepGroupMemberRepository memberRepository;
    private final JobStepGroupEventRepository eventRepository;
    private final ProductionJobRepository jobRepository;
    private final PieceRecordRepository recordRepository;
    private final GroupViewAssembler viewAssembler;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GroupView view(JobStepGroup group) {
        return viewAssembler.toView(group);
    }

    @Transactional
    public JobStepGroup openOrJoin(ProductionJob job, int stepNo, Employee employee, BigDecimal qty) {
        JobStepGroup group = groupRepository.findByJobIdAndStepNo(job.getId(), stepNo).orElse(null);
        if (group != null && group.getStatus() != GroupStatus.OPEN) {
            throw ApiException.conflict("step_done", "该工序合报已经结束，不能重复报");
        }
        if (group == null) {
            group = new JobStepGroup();
            group.setId(Ids.groupId());
            group.setJobId(job.getId());
            group.setStepNo(stepNo);
            group.setStatus(GroupStatus.OPEN);
            group.setTotalQty(qty == null ? BigDecimal.ZERO : qty);
            group.setUnit(job.getUnit());
            group.setCreatedBy(employee.getId());
            group.setCreatedAt(Instant.now(clock));
            groupRepository.save(group);
            recordEvent(group, GroupStatus.OPEN, GroupStatus.OPEN, "OPEN", employee.getId(), "开组");
            auditService.record(employee.getId(), "group_open", "job", job.getId(), "step=" + stepNo);
        }

        Optional<JobStepGroupMember> existing =
                memberRepository.findByGroupIdAndEmployeeId(group.getId(), employee.getId());
        if (existing.isEmpty()) {
            JobStepGroupMember member = new JobStepGroupMember();
            member.setGroupId(group.getId());
            member.setEmployeeId(employee.getId());
            member.setJoinedAt(Instant.now(clock));
            memberRepository.save(member);
            transition(group, GroupAction.JOIN, employee.getId(), "加入合报");
            auditService.record(employee.getId(), "group_join", "job", job.getId(), "step=" + stepNo);
        }
        return group;
    }

    @Transactional
    public JobStepGroup closeGroup(UserPrincipal actor, String jobId, int stepNo, String reason) {
        ProductionJob job = requireJob(jobId);
        requireManage(actor, job);
        JobStepGroup group = groupRepository.findByJobIdAndStepNo(jobId, stepNo)
                .orElseThrow(() -> ApiException.notFound("group_not_found", "该工序没有合报组"));
        if (group.getStatus() == GroupStatus.CLOSED) {
            return group;
        }
        if (group.getStatus() == GroupStatus.CANCELLED) {
            throw ApiException.badRequest("group_cancelled", "合报组已取消，不能结束");
        }
        if (group.getStatus() == GroupStatus.SETTLING) {
            throw ApiException.conflict("group_settling", "合报正在结算，请稍后刷新");
        }
        settle(job, group, actor.id(), defaultReason(reason));
        return group;
    }

    @Transactional
    public void closeAllOpenGroups(ProductionJob job, UserPrincipal actor, String reason) {
        List<JobStepGroup> groups = groupRepository
                .findByJobIdAndStatusOrderByStepNoAsc(job.getId(), GroupStatus.OPEN);
        for (JobStepGroup group : groups) {
            settle(job, group, actor.id(), defaultReason(reason));
        }
    }

    @Transactional
    public void closeOpenGroupsBefore(ProductionJob job, int stepNo, UserPrincipal actor) {
        List<JobStepGroup> groups = groupRepository
                .findByJobIdAndStatusAndStepNoLessThanOrderByStepNoAsc(
                        job.getId(), GroupStatus.OPEN, stepNo);
        for (JobStepGroup group : groups) {
            settle(job, group, actor.id(), "下一道工序开始，自动结束");
        }
    }

    @Transactional
    public void cancelOpenGroups(ProductionJob job, UserPrincipal actor, String reason) {
        List<JobStepGroup> groups = groupRepository
                .findByJobIdAndStatusOrderByStepNoAsc(job.getId(), GroupStatus.OPEN);
        for (JobStepGroup group : groups) {
            group.setClosedBy(actor.id());
            group.setClosedAt(Instant.now(clock));
            group.setCloseReason(reason);
            transition(group, GroupAction.CANCEL, actor.id(), reason);
        }
    }

    @Transactional
    public void removeMember(UserPrincipal actor, String jobId, int stepNo, String employeeId) {
        ProductionJob job = requireJob(jobId);
        requireManage(actor, job);
        JobStepGroup group = groupRepository.findByJobIdAndStepNo(jobId, stepNo)
                .orElseThrow(() -> ApiException.badRequest("group_closed", "合报组不存在或已结束"));
        if (group.getStatus() != GroupStatus.OPEN) {
            throw ApiException.badRequest("group_closed", "合报组不存在或已结束");
        }
        memberRepository.deleteByGroupIdAndEmployeeId(group.getId(), employeeId);
        auditService.record(actor.id(), "group_remove_member", "employee", employeeId, group.getId());
    }

    @Transactional(readOnly = true)
    public boolean canManage(UserPrincipal actor, ProductionJob job) {
        return actor.admin() || (job.getCreatedBy() != null && job.getCreatedBy().equals(actor.id()));
    }

    private void settle(ProductionJob job, JobStepGroup group, String actorId, String reason) {
        transition(group, GroupAction.BEGIN_SETTLE, actorId, reason);

        List<JobStepGroupMember> members =
                memberRepository.findByGroupIdOrderByJoinedAtAsc(group.getId());
        BigDecimal total = orderQuantity(job);
        List<BigDecimal> shares = SettlementCalculator.split(total, members.size());

        for (int index = 0; index < members.size(); index++) {
            JobStepGroupMember member = members.get(index);
            BigDecimal qty = shares.get(index);
            String idempotencyKey = Ids.groupRecordKey(group.getId(), member.getEmployeeId());
            PieceRecord record = recordRepository.findByIdempotencyKey(idempotencyKey).orElseGet(() -> {
                PieceRecord created = new PieceRecord();
                created.setId(Ids.recordId());
                created.setJobId(job.getId());
                created.setStepNo(group.getStepNo());
                created.setLine(job.getLine());
                created.setEmployeeId(member.getEmployeeId());
                created.setQty(qty);
                created.setUnit(group.getUnit() == null ? job.getUnit() : group.getUnit());
                created.setStatus(RecordStatus.VALID);
                created.setNote("合报平分：" + reason);
                created.setIdempotencyKey(idempotencyKey);
                created.setSource("group");
                created.setGroupId(group.getId());
                created.setCreatedAt(Instant.now(clock));
                return recordRepository.save(created);
            });
            member.setRecordId(record.getId());
            memberRepository.save(member);
            auditService.record(actorId, "group_split", "record", record.getId(),
                    group.getId() + "/" + qty);
        }

        group.setClosedBy(actorId);
        group.setClosedAt(Instant.now(clock));
        group.setCloseReason(reason);
        transition(group, GroupAction.SETTLED, actorId, reason);
        auditService.record(actorId, "group_close", "group", group.getId(),
                "members=" + members.size() + "/total=" + total);
    }

    private JobStepGroup transition(JobStepGroup group, GroupAction action, String actorId, String reason) {
        GroupStatus from = group.getStatus();
        GroupStatus to = GroupStateMachine.next(from, action);
        group.setStatus(to);
        groupRepository.save(group);
        recordEvent(group, from, to, action.name(), actorId, reason);
        return group;
    }

    private void recordEvent(
            JobStepGroup group,
            GroupStatus from,
            GroupStatus to,
            String action,
            String actorId,
            String reason
    ) {
        JobStepGroupEvent event = new JobStepGroupEvent();
        event.setId(Ids.eventId());
        event.setGroupId(group.getId());
        event.setFromState(from);
        event.setToState(to);
        event.setAction(action);
        event.setActorId(actorId);
        event.setReason(reason);
        event.setCreatedAt(Instant.now(clock));
        eventRepository.save(event);
    }

    private ProductionJob requireJob(String jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> ApiException.notFound("not_found", "订单不存在"));
    }

    private void requireManage(UserPrincipal actor, ProductionJob job) {
        if (!canManage(actor, job)) {
            throw ApiException.forbidden("只有厂长或建单人才能管理合报");
        }
    }

    private BigDecimal orderQuantity(ProductionJob job) {
        if (job.getMeters() != null && job.getMeters().compareTo(BigDecimal.ZERO) > 0) {
            return job.getMeters();
        }
        return job.getPlannedQty() == null ? BigDecimal.ZERO : job.getPlannedQty();
    }

    private String defaultReason(String reason) {
        return reason == null || reason.isBlank() ? "手动结束合报" : reason;
    }
}
