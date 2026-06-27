package zw.gov.mohcc.impilo.mvumo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LegalAgreementRepository extends JpaRepository<LegalAgreementEntity, UUID> {

    List<LegalAgreementEntity> findByTenantIdAndSubjectActorIdOrderByCreatedAtDesc(
            UUID tenantId, String subjectActorId);

    Optional<LegalAgreementEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** Currently-GRANTED acceptances for a subject + document type (normally 0 or 1). */
    List<LegalAgreementEntity> findByTenantIdAndSubjectActorIdAndDocumentTypeAndState(
            UUID tenantId, String subjectActorId, String documentType, String state);
}
