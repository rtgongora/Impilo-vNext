package zw.gov.mohcc.impilo.indawo.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.indawo.domain.SiteRelationshipEntity;

import java.util.List;
import java.util.UUID;

public interface SiteRelationshipRepository extends JpaRepository<SiteRelationshipEntity, UUID> {

    List<SiteRelationshipEntity> findByParentSiteId(UUID parentSiteId);

    List<SiteRelationshipEntity> findByChildSiteId(UUID childSiteId);
}
