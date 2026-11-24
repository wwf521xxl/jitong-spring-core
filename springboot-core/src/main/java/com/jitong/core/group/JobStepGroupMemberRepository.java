package com.jitong.core.group;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobStepGroupMemberRepository extends JpaRepository<JobStepGroupMember, Long> {

    List<JobStepGroupMember> findByGroupIdOrderByJoinedAtAsc(String groupId);

    List<JobStepGroupMember> findByGroupIdInOrderByJoinedAtAsc(Collection<String> groupIds);

    Optional<JobStepGroupMember> findByGroupIdAndEmployeeId(String groupId, String employeeId);

    long countByGroupId(String groupId);

    void deleteByGroupIdAndEmployeeId(String groupId, String employeeId);
}
