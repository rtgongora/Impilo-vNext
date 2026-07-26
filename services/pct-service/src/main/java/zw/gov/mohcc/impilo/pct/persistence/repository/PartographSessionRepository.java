package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.PartographSessionEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartographSessionRepository extends JpaRepository<PartographSessionEntity, UUID> {

    Optional<PartographSessionEntity> findByTenantIdAndSubjectCpidAndStatus(
            UUID tenantId, String subjectCpid, String status);

    List<PartographSessionEntity> findByTenantIdAndSubjectCpidOrderByStartedAtDesc(UUID tenantId, String subjectCpid);
}
