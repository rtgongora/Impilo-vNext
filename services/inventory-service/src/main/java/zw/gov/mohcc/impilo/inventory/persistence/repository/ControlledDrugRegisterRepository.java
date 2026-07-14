package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.persistence.entity.ControlledDrugRegisterEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for the controlled-drug witness register (inv_controlled_drug_register).
 */
@Repository
public interface ControlledDrugRegisterRepository extends JpaRepository<ControlledDrugRegisterEntity, UUID> {

    List<ControlledDrugRegisterEntity> findByTenantIdAndRefTypeAndRefIdOrderByCreatedAtAsc(
            UUID tenantId, String refType, String refId);

    /**
     * Latest entry for a drug on a reference — used to carry the running balance forward.
     */
    Optional<ControlledDrugRegisterEntity> findFirstByTenantIdAndItemCodeAndRefIdOrderByCreatedAtDesc(
            UUID tenantId, String itemCode, String refId);
}
