package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.Allergy;

import java.util.List;
import java.util.UUID;

public interface AllergyRepository extends JpaRepository<Allergy, UUID> {

    List<Allergy> findByTenantIdAndPatientId(String tenantId, UUID patientId);
}
