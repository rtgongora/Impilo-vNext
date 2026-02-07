package zw.gov.mohcc.impilo.pharmacy.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pharmacy.domain.DispenseStatus;
import zw.gov.mohcc.impilo.pharmacy.domain.OrderPriority;
import zw.gov.mohcc.impilo.pharmacy.persistence.entity.DispenseOrderEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispenseOrderRepository extends JpaRepository<DispenseOrderEntity, UUID> {

    Optional<DispenseOrderEntity> findByOrosOrderId(String orosOrderId);

    Page<DispenseOrderEntity> findByFacilityIdAndStatus(UUID facilityId, DispenseStatus status, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityId(UUID facilityId, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityIdAndWorkspaceIdAndStatus(
            UUID facilityId, UUID workspaceId, DispenseStatus status, Pageable pageable);

    List<DispenseOrderEntity> findByPatientCpidOrderByCreatedAtDesc(String patientCpid);

    Page<DispenseOrderEntity> findByFacilityIdAndPriority(UUID facilityId, OrderPriority priority, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityIdAndStatusAndPriority(
            UUID facilityId, DispenseStatus status, OrderPriority priority, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityIdAndWorkspaceId(UUID facilityId, UUID workspaceId, Pageable pageable);

    Page<DispenseOrderEntity> findByFacilityIdAndWorkspaceIdAndStatusAndPriority(
            UUID facilityId, UUID workspaceId, DispenseStatus status, OrderPriority priority, Pageable pageable);
}
