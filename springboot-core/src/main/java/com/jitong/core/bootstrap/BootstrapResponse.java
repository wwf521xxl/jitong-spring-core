package com.jitong.core.bootstrap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jitong.core.catalog.RouteView;
import com.jitong.core.catalog.SkillView;
import com.jitong.core.employee.EmployeeView;
import com.jitong.core.group.GroupView;
import com.jitong.core.job.JobView;
import com.jitong.core.report.CorrectionView;
import com.jitong.core.report.RecordView;
import com.jitong.core.settings.SettingsView;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 前端首屏聚合响应。
 * 可选字段用 NON_NULL：登录多一个 token，写操作多一个 job/record/group。
 */
@Getter
@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BootstrapResponse {

    private final EmployeeView user;
    private final List<EmployeeView> employees;
    private final List<SkillView> skills;
    private final List<RouteView> routes;
    private final List<JobView> jobs;
    private final List<RecordView> records;
    private final List<CorrectionView> corrections;
    private final SettingsView settings;

    private final String token;
    private final JobView job;
    private final RecordView record;
    private final GroupView group;
}
