package zw.gov.mohcc.impilo.support.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.support.domain.SupportPermittedActionEntity;

import java.util.List;
import java.util.UUID;

public interface SupportPermittedActionRepository extends JpaRepository<SupportPermittedActionEntity, UUID> {
    List<SupportPermittedActionEntity> findByTeamId(UUID teamId);
}
