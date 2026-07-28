package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.ConfigApprovalEntity;

import java.util.List;
import java.util.UUID;

public interface ConfigApprovalRepository extends JpaRepository<ConfigApprovalEntity, UUID> {

    List<ConfigApprovalEntity> findByReleaseId(UUID releaseId);

    boolean existsByReleaseIdAndApproverHealthId(UUID releaseId, String approverHealthId);
}
