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

    boolean existsByFacilityUuidAndApprovalState(UUID facilityUuid, String approvalState);

    boolean existsByFacilityUuidAndPersonHealthIdAndApprovalState(
            UUID facilityUuid, String personHealthId, String approvalState);
}
