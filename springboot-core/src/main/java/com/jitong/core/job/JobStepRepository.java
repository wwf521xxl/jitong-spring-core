package com.jitong.core.job;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStepRepository extends JpaRepository<JobStep, Long> {

    List<JobStep> findByJobIdOrderByStepNoAsc(String jobId);

    Optional<JobStep> findByJobIdAndStepNo(String jobId, int stepNo);

    List<JobStep> findByJobIdInOrderByJobIdAscStepNoAsc(Collection<String> jobIds);

    long countByStepName(String stepName);
}
