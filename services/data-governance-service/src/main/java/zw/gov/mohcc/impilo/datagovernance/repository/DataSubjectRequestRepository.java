package zw.gov.mohcc.impilo.datagovernance.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.datagovernance.domain.DataSubjectRequestEntity;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface DataSubjectRequestRepository extends JpaRepository<DataSubjectRequestEntity, UUID> {

    /** Most recent request for the subject, regardless of status — drives the status endpoint. */
    Optional<DataSubjectRequestEntity> findFirstByTenantIdAndSubjectIdAndRequestTypeOrderByRequestedAtDesc(
            UUID tenantId, String subjectId, String requestType);

    /** Most recent in-flight request for the subject — used to enforce single-active and to cancel. */
    Optional<DataSubjectRequestEntity> findFirstByTenantIdAndSubjectIdAndRequestTypeAndStatusInOrderByRequestedAtDesc(
            UUID tenantId, String subjectId, String requestType, Collection<String> statuses);
}
