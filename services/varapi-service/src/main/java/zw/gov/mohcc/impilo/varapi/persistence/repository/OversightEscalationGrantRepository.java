package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.OversightEscalationGrantEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface OversightEscalationGrantRepository extends JpaRepository<OversightEscalationGrantEntity, Long> {
    List<OversightEscalationGrantEntity> findByGrantedToOrgIdAndStatus(UUID grantedToOrgId, String status);
    boolean existsByTenantIdAndSubjectTypeAndSubjectRefAndGrantedToOrgIdAndStatus(
            UUID tenantId, String subjectType, String subjectRef, UUID grantedToOrgId, String status);
}
