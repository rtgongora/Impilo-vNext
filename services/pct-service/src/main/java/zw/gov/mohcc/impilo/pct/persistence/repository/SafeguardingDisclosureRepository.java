package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.SafeguardingDisclosureEntity;

import java.util.List;
import java.util.UUID;

public interface SafeguardingDisclosureRepository extends JpaRepository<SafeguardingDisclosureEntity, UUID> {
    List<SafeguardingDisclosureEntity> findByTenantIdAndSubjectCpidOrderByCreatedAtDesc(UUID tenantId, String subjectCpid);
}
