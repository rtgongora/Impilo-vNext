package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SiteStatusHistoryEntity;

import java.util.List;
import java.util.UUID;

public interface SiteStatusHistoryRepository extends JpaRepository<SiteStatusHistoryEntity, UUID> {
    List<SiteStatusHistoryEntity> findBySiteIdOrderByChangedAtDesc(UUID siteId);
}

