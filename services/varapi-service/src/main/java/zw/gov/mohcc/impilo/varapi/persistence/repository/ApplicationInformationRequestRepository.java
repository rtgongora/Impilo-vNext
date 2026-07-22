package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationInformationRequestEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationInformationRequestRepository extends JpaRepository<ApplicationInformationRequestEntity, Long> {
    List<ApplicationInformationRequestEntity> findByTenantIdAndApplicationIdOrderByCreatedAtDesc(UUID tenantId, Long applicationId);
}
