package zw.gov.mohcc.impilo.varapi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.varapi.persistence.entity.ProviderEligibilitySnapshotEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProviderEligibilitySnapshotRepository
        extends JpaRepository<ProviderEligibilitySnapshotEntity, UUID> {

    List<ProviderEligibilitySnapshotEntity> findByProviderPublicIdOrderByAssessedAtDesc(String providerPublicId);
}
