package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.CareTransferEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CareTransferRepository extends JpaRepository<CareTransferEntity, UUID> {

    Optional<CareTransferEntity> findByTenantIdAndTransferId(UUID tenantId, UUID transferId);

    List<CareTransferEntity> findByTenantIdAndSubjectCpidOrderByRequestedAtDesc(UUID tenantId, String subjectCpid);

    /** Receiving service's inbox: open transfer requests awaiting acceptance. */
    List<CareTransferEntity> findByTenantIdAndToServiceAndStatusOrderByRequestedAtAsc(
            UUID tenantId, String toService, String status);

    Optional<CareTransferEntity> findByTenantIdAndSubjectCpidAndToServiceAndStatus(
            UUID tenantId, String subjectCpid, String toService, String status);

    Optional<CareTransferEntity> findByTenantIdAndConsultationIdAndStatus(
            UUID tenantId, UUID consultationId, String status);
}
