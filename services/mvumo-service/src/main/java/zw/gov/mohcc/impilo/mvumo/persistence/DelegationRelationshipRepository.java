package zw.gov.mohcc.impilo.mvumo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DelegationRelationshipRepository extends JpaRepository<DelegationRelationshipEntity, UUID> {

    Optional<DelegationRelationshipEntity> findByIdAndTenantId(UUID id, UUID tenantId);

    /** A delegate's relationships (the "acting for" list). */
    List<DelegationRelationshipEntity> findByTenantIdAndDelegateActorIdOrderByCreatedAtDesc(
            UUID tenantId, String delegateActorId);

    /** A subject's delegations (the "who can act for me" review surface). */
    List<DelegationRelationshipEntity> findByTenantIdAndDelegatorSubjectRefOrderByCreatedAtDesc(
            UUID tenantId, String delegatorSubjectRef);

    /** Resolve candidate relationships for "delegate D acting for subject S" (hot path). */
    List<DelegationRelationshipEntity> findByTenantIdAndDelegateActorIdAndDelegatorSubjectRefAndStatus(
            UUID tenantId, String delegateActorId, String delegatorSubjectRef, String status);
}
