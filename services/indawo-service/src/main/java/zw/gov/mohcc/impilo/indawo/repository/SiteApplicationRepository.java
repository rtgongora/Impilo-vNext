package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SiteApplicationEntity;

import java.util.List;
import java.util.UUID;

public interface SiteApplicationRepository extends JpaRepository<SiteApplicationEntity, UUID> {
    List<SiteApplicationEntity> findBySiteIdOrderByCreatedAtDesc(UUID siteId);
}

