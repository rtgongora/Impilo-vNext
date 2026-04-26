package zw.gov.mohcc.impilo.costa.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.costa.domain.entity.ChargeSheetEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChargeSheetRepository extends JpaRepository<ChargeSheetEntity, String> {

    List<ChargeSheetEntity> findByTenantIdAndPatientCpidOrderByCreatedAtDesc(UUID tenantId, String patientCpid);

    Optional<ChargeSheetEntity> findByChargeSheetIdAndTenantId(String chargeSheetId, UUID tenantId);
}
