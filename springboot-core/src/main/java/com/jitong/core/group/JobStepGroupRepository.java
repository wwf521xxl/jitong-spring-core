package com.jitong.core.group;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStepGroupRepository extends JpaRepository<JobStepGroup, String> {

    Optional<JobStepGroup> findByJobIdAndStepNo(String jobId, int stepNo);

    List<JobStepGroup> findByJobIdAndStatusOrderByStepNoAsc(String jobId, GroupStatus status);

    List<JobStepGroup> findByJobIdAndStatusAndStepNoLessThanOrderByStepNoAsc(
            String jobId, GroupStatus status, int stepNo);

    List<JobStepGroup> findByJobIdIn(java.util.Collection<String> jobIds);
}
