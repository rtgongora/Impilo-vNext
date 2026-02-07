package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.SubstitutionRuleEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface SubstitutionRuleRepository extends JpaRepository<SubstitutionRuleEntity, UUID> {

    List<SubstitutionRuleEntity> findByTenantIdAndSourceCodeAndEnabledTrue(UUID tenantId, String sourceCode);

    List<SubstitutionRuleEntity> findByTenantIdAndFacilityIdAndSourceCodeAndEnabledTrue(
            UUID tenantId, UUID facilityId, String sourceCode);
}
