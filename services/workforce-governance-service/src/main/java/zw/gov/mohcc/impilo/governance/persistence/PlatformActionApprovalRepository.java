package zw.gov.mohcc.impilo.governance.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PlatformActionApprovalRepository extends JpaRepository<PlatformActionApprovalEntity, UUID> {

    List<PlatformActionApprovalEntity> findByAccessRequestIdOrderByDecidedAtAsc(UUID accessRequestId);

    boolean existsByAccessRequestIdAndApproverUserId(UUID accessRequestId, String approverUserId);
}
