package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.PatientAccountEntity;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PatientAccountRepository extends JpaRepository<PatientAccountEntity, Long> {
    Optional<PatientAccountEntity> findByTenantIdAndPatientCpid(UUID tenantId, String patientCpid);

    Optional<PatientAccountEntity> findByAccountId(UUID accountId);
}
