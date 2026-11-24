package com.jitong.core.job;

import com.jitong.core.bootstrap.BootstrapResponse;
import com.jitong.core.bootstrap.BootstrapService;
import com.jitong.core.group.GroupService;
import com.jitong.core.group.JobStepGroup;
import com.jitong.core.report.AutoReportResult;
import com.jitong.core.report.ReportingService;
import com.jitong.core.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final ReportingService reportingService;
    private final GroupService groupService;
    private final BootstrapService bootstrapService;

    @PostMapping({"/api/jobs", "/api/admin/jobs"})
    public BootstrapResponse createJob(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody JobCreateRequest body
    ) {
        JobView job = jobService.createJob(user, new JobCreateCommand(
                body.unit(), body.line(), body.orderNo(), body.remark(), body.meters(), body.routeKey()));
        return bootstrapService.build(user).toBuilder().job(job).build();
    }

    @GetMapping("/api/jobs/{jobId}/auto-report/options")
    public AutoReportOptions autoReportOptions(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String jobId
    ) {
        return jobService.autoReportOptions(user, jobId);
    }

    @PostMapping("/api/jobs/{jobId}/auto-report")
    public BootstrapResponse autoReport(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String jobId,
            @RequestBody(required = false) AutoReportRequest body
    ) {
        AutoReportResult result = reportingService.autoReport(
                user, jobId, body == null ? null : body.stepNo());
        BootstrapResponse.BootstrapResponseBuilder builder = bootstrapService.build(user).toBuilder();
        if (result.record() != null) {
            builder.record(result.record());
        }
        if (result.group() != null) {
            builder.group(result.group());
        }
        return builder.build();
    }

    @PatchMapping("/api/admin/jobs/{jobId}")
    @PreAuthorize("hasRole('ADMIN')")
    public BootstrapResponse editJob(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String jobId,
            @RequestBody JobEditRequest body
    ) {
        JobView job = jobService.editJob(user, jobId,
                new JobEditCommand(body.orderNo(), body.remark(), body.meters()));
        return bootstrapService.build(user).toBuilder().job(job).build();
    }

    @PostMapping("/api/admin/jobs/{jobId}/void")
    @PreAuthorize("hasRole('ADMIN')")
    public BootstrapResponse voidJob(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String jobId,
            @RequestBody(required = false) VoidJobRequest body
    ) {
        JobView job = jobService.voidJob(user, jobId, body == null ? null : body.reason());
        return bootstrapService.build(user).toBuilder().job(job).build();
    }

    @PostMapping("/api/admin/jobs/{jobId}/complete")
    public BootstrapResponse completeJob(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String jobId
    ) {
        JobView job = jobService.completeJob(user, jobId);
        return bootstrapService.build(user).toBuilder().job(job).build();
    }

    @PostMapping("/api/admin/jobs/{jobId}/groups/{stepNo}/close")
    public BootstrapResponse closeGroup(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String jobId,
            @PathVariable int stepNo,
            @RequestBody(required = false) GroupCloseRequest body
    ) {
        String reason = body == null ? null : body.reason();
        JobStepGroup group = groupService.closeGroup(user, jobId, stepNo, reason);
        ProductionJob job = jobService.requireJob(jobId);
        return bootstrapService.build(user).toBuilder()
                .job(jobService.view(job))
                .group(groupService.view(group))
                .build();
    }

    @PostMapping("/api/admin/jobs/{jobId}/groups/{stepNo}/members/{employeeId}/remove")
    public BootstrapResponse removeGroupMember(
            @AuthenticationPrincipal UserPrincipal user,
            @PathVariable String jobId,
            @PathVariable int stepNo,
            @PathVariable String employeeId
    ) {
        groupService.removeMember(user, jobId, stepNo, employeeId);
        return bootstrapService.build(user);
    }

    public record GroupCloseRequest(String reason) {
    }
}
