package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.PatientImplantEntity;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Repository for patient/episode implant links (inv_patient_implant).
 */
@Repository
public interface PatientImplantRepository extends JpaRepository<PatientImplantEntity, UUID> {

    List<PatientImplantEntity> findByTenantIdAndPatientCpidOrderByImplantedAtDesc(UUID tenantId, String patientCpid);

    List<PatientImplantEntity> findByTenantIdAndImplantUnitIdInOrderByImplantedAtDesc(UUID tenantId,
                                                                                      Collection<UUID> implantUnitIds);
}
