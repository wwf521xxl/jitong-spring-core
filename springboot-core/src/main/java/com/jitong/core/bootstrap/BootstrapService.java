package com.jitong.core.bootstrap;

import com.jitong.core.catalog.CatalogService;
import com.jitong.core.common.TimeFormat;
import com.jitong.core.config.JitongProperties;
import com.jitong.core.employee.Employee;
import com.jitong.core.employee.EmployeeService;
import com.jitong.core.employee.EmployeeView;
import com.jitong.core.job.JobStatus;
import com.jitong.core.job.JobViewAssembler;
import com.jitong.core.job.ProductionJob;
import com.jitong.core.job.ProductionJobRepository;
import com.jitong.core.report.CorrectionRepository;
import com.jitong.core.report.PieceRecordRepository;
import com.jitong.core.report.RecordViewAssembler;
import com.jitong.core.security.UserPrincipal;
import com.jitong.core.settings.SettingsService;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BootstrapService {

    private static final int MAX_RECORDS = 20_000;
    private static final int MAX_CORRECTIONS = 1_000;

    private final EmployeeService employeeService;
    private final CatalogService catalogService;
    private final ProductionJobRepository jobRepository;
    private final PieceRecordRepository recordRepository;
    private final CorrectionRepository correctionRepository;
    private final JobViewAssembler jobViewAssembler;
    private final RecordViewAssembler recordViewAssembler;
    private final SettingsService settingsService;
    private final JitongProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public BootstrapResponse build(UserPrincipal principal) {
        Employee user = employeeService.require(principal.id());
        EmployeeView userView = employeeService.view(user);
        List<EmployeeView> employees = employeeService.activeViews();
        List<ProductionJob> jobs = jobRepository.findBootstrap(cutoff(), JobStatus.ACTIVE);
        Instant cutoff = cutoff();

        return BootstrapResponse.builder()
                .user(userView)
                .employees(employees)
                .skills(catalogService.skills())
                .routes(catalogService.routes())
                .jobs(jobViewAssembler.assemble(jobs))
                .records(recordRepository.findBootstrap(cutoff, JobStatus.ACTIVE,
                                PageRequest.of(0, MAX_RECORDS)).stream()
                        .map(recordViewAssembler::toView)
                        .toList())
                .corrections(correctionRepository.findBootstrap(cutoff, PageRequest.of(0, MAX_CORRECTIONS))
                        .stream()
                        .map(recordViewAssembler::toView)
                        .toList())
                .settings(settingsService.view())
                .build();
    }

    public BootstrapResponse withToken(BootstrapResponse response, String token) {
        return response.toBuilder().token(token).build();
    }

    private Instant cutoff() {
        return TimeFormat.nowSeconds(clock).minus(properties.getBootstrapDays(), ChronoUnit.DAYS);
    }
}
