package com.jitong.core.group;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStepGroupEventRepository extends JpaRepository<JobStepGroupEvent, String> {

    List<JobStepGroupEvent> findByGroupIdOrderByCreatedAtAsc(String groupId);
}
