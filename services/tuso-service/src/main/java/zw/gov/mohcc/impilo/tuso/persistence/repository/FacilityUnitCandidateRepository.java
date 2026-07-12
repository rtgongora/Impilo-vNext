package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityUnitCandidateEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FacilityUnitCandidateRepository extends JpaRepository<FacilityUnitCandidateEntity, UUID> {

    List<FacilityUnitCandidateEntity> findByFacilityIdOrderByCreatedAtDesc(Long facilityId);

    List<FacilityUnitCandidateEntity> findByFacilityIdAndStatus(Long facilityId, String status);

    Optional<FacilityUnitCandidateEntity> findByCandidateId(UUID candidateId);
}
