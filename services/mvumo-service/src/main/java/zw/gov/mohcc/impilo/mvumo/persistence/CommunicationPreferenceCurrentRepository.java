package zw.gov.mohcc.impilo.mvumo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CommunicationPreferenceCurrentRepository
        extends JpaRepository<
                CommunicationPreferenceCurrentEntity,
                CommunicationPreferenceCurrentEntity.CommunicationPreferenceCurrentId> {

    Optional<CommunicationPreferenceCurrentEntity> findByTenantIdAndPatientRef(UUID tenantId, String patientRef);
}
