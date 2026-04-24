package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SiteOperatorEntity;

import java.util.List;
import java.util.UUID;

public interface SiteOperatorRepository extends JpaRepository<SiteOperatorEntity, UUID> {
    List<SiteOperatorEntity> findBySiteIdOrderByUpdatedAtDesc(UUID siteId);
}

