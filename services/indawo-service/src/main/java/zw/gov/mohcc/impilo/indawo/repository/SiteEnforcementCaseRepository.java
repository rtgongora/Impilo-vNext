package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SiteEnforcementCaseEntity;

import java.util.List;
import java.util.UUID;

public interface SiteEnforcementCaseRepository extends JpaRepository<SiteEnforcementCaseEntity, UUID> {
    List<SiteEnforcementCaseEntity> findBySiteIdOrderByOpenedAtDesc(UUID siteId);
}

