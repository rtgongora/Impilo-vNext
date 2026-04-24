package zw.gov.mohcc.impilo.tshepo.authz.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import zw.gov.mohcc.impilo.tshepo.authz.persistence.entity.VisibilityEscalationGrantEntity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface VisibilityEscalationGrantRepository extends JpaRepository<VisibilityEscalationGrantEntity, Long> {

    @Query("""
            SELECT g FROM VisibilityEscalationGrantEntity g
            WHERE g.grantToken = :token AND g.tenantId = :tenantId AND g.actorId = :actorId
              AND g.status = 'ACTIVE' AND g.expiresAt > :now
            """)
    Optional<VisibilityEscalationGrantEntity> findActiveGrant(
            @Param("token") UUID grantToken,
            @Param("tenantId") UUID tenantId,
            @Param("actorId") String actorId,
            @Param("now") Instant now);
}
