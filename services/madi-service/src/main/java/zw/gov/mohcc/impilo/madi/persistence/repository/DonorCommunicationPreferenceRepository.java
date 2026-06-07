package zw.gov.mohcc.impilo.madi.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.madi.persistence.entity.DonorCommunicationPreferenceEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DonorCommunicationPreferenceRepository extends JpaRepository<DonorCommunicationPreferenceEntity, Long> {
    Optional<DonorCommunicationPreferenceEntity> findByPreferenceIdAndTenantId(UUID preferenceId, UUID tenantId);
    Optional<DonorCommunicationPreferenceEntity> findByTenantIdAndDonorIdAndChannel(UUID tenantId, UUID donorId, String channel);
    List<DonorCommunicationPreferenceEntity> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
