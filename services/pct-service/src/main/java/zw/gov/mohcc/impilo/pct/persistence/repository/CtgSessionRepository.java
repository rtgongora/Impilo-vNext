package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.CtgSessionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CtgSessionRepository extends JpaRepository<CtgSessionEntity, UUID> {

    Optional<CtgSessionEntity> findByTenantIdAndSubjectCpidAndStatus(
            UUID tenantId, String subjectCpid, String status);

    List<CtgSessionEntity> findByTenantIdAndSubjectCpidOrderByStartedAtDesc(UUID tenantId, String subjectCpid);
}
