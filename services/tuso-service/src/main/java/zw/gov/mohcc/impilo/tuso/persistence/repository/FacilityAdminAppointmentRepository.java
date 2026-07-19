package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.FacilityAdminAppointmentEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface FacilityAdminAppointmentRepository
        extends JpaRepository<FacilityAdminAppointmentEntity, Long> {

    List<FacilityAdminAppointmentEntity> findByFacilityUuidOrderByCreatedAtDesc(UUID facilityUuid);

    /** Cross-facility review queue by approval state (Trust Console), newest first. */
    List<FacilityAdminAppointmentEntity> findByApprovalStateOrderByCreatedAtDesc(String approvalState);

    boolean existsByFacilityUuidAndApprovalState(UUID facilityUuid, String approvalState);

    boolean existsByFacilityUuidAndPersonHealthIdAndApprovalState(
            UUID facilityUuid, String personHealthId, String approvalState);

    /** Role-aware self-conflict check (V030: distinct roles may coexist). */
    boolean existsByFacilityUuidAndPersonHealthIdAndRoleAndApprovalState(
            UUID facilityUuid, String personHealthId, String role, String approvalState);

    /** Expiry sweep feed (V030): ACTIVE appointments whose validity window has passed. */
    List<FacilityAdminAppointmentEntity> findByApprovalStateAndValidToBefore(
            String approvalState, java.time.LocalDate validTo);
}
