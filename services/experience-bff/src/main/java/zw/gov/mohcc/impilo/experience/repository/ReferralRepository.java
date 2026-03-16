package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.Referral;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReferralRepository extends JpaRepository<Referral, UUID> {

    List<Referral> findByEncounterId(UUID encounterId);

    Page<Referral> findByTenantIdAndPatientId(String tenantId, UUID patientId, Pageable pageable);

    Optional<Referral> findByIdAndTenantId(UUID id, String tenantId);
}
