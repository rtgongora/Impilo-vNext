package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.OfferingEntity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OfferingRepository extends JpaRepository<OfferingEntity, String> {
    List<OfferingEntity> findByCatalogItemIdAndActiveOrderByUpdatedAtDesc(String catalogItemId, boolean active);
    List<OfferingEntity> findByTenantIdAndActiveOrderByUpdatedAtDesc(UUID tenantId, boolean active);
    Optional<OfferingEntity> findByOfferingId(String offeringId);
}

