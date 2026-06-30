package zw.gov.mohcc.impilo.daidzai.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.daidzai.persistence.entity.AffectedSiteEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface AffectedSiteRepository extends JpaRepository<AffectedSiteEntity, UUID> {
    List<AffectedSiteEntity> findByIncidentIdOrderByCreatedAtAsc(UUID incidentId);
}
