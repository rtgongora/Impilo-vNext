package zw.gov.mohcc.impilo.tshepo.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface ConsentDirectiveRepository extends JpaRepository<ConsentDirectiveEntity, Long> {

    /**
     * Check if an active consent directive exists that covers the requested access.
     */
    @Query("""
        SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END
        FROM ConsentDirectiveEntity c
        WHERE c.tenantId = :tenantId
          AND c.subjectCpid = :subjectCpid
          AND c.status = 'ACTIVE'
          AND (c.validTo IS NULL OR c.validTo > CURRENT_TIMESTAMP)
          AND (c.granteeScope = :actorId OR c.granteeScope = '*')
          AND (c.purposeOfUse = :purpose OR c.purposeOfUse = '*')
        """)
    boolean existsActiveConsent(
        @Param("tenantId") UUID tenantId,
        @Param("subjectCpid") String subjectCpid,
        @Param("actorId") String actorId,
        @Param("purpose") String purpose
    );
}
