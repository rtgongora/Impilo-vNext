package zw.gov.mohcc.impilo.orgregistry.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.orgregistry.persistence.entity.TrustDomainMembershipEntity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TrustDomainMembershipRepository
        extends JpaRepository<TrustDomainMembershipEntity, UUID> {

    /** The live membership for a subject, if one exists. Absence means unmapped. */
    @Query("select m from TrustDomainMembershipEntity m where m.subjectType = :subjectType "
            + "and m.subjectId = :subjectId and m.status = 'ACTIVE' "
            + "and m.effectiveFrom <= :at and (m.effectiveTo is null or m.effectiveTo > :at)")
    Optional<TrustDomainMembershipEntity> findActiveFor(@Param("subjectType") String subjectType,
                                                        @Param("subjectId") UUID subjectId,
                                                        @Param("at") OffsetDateTime at);

    /** Every membership record for a subject, newest first — history is never deleted. */
    List<TrustDomainMembershipEntity> findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(
            String subjectType, UUID subjectId);

    /** The review queues the governance workspace reads. */
    List<TrustDomainMembershipEntity> findByStatus(String status);
}
