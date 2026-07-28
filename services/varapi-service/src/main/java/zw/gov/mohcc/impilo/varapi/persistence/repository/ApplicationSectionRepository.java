package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ApplicationSectionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApplicationSectionRepository extends JpaRepository<ApplicationSectionEntity, Long> {

    List<ApplicationSectionEntity> findByTenantIdAndApplicationIdOrderBySequenceNoAsc(
            UUID tenantId, Long applicationId);

    Optional<ApplicationSectionEntity> findByTenantIdAndApplicationIdAndSectionKey(
            UUID tenantId, Long applicationId, String sectionKey);

    List<ApplicationSectionEntity> findByTenantIdAndApplicationIdAndSectionKeyInOrderBySequenceNoAsc(
            UUID tenantId, Long applicationId, List<String> sectionKeys);

    long countByTenantIdAndApplicationIdAndStateNot(UUID tenantId, Long applicationId, String state);
}
