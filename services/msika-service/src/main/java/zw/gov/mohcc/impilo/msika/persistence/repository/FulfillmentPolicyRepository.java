package zw.gov.mohcc.impilo.msika.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msika.persistence.entity.FulfillmentPolicyEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface FulfillmentPolicyRepository extends JpaRepository<FulfillmentPolicyEntity, String> {
    List<FulfillmentPolicyEntity> findByCatalogItemIdAndActiveOrderByUpdatedAtDesc(String catalogItemId, boolean active);
    List<FulfillmentPolicyEntity> findByOfferingIdAndActiveOrderByUpdatedAtDesc(String offeringId, boolean active);
    Optional<FulfillmentPolicyEntity> findByPolicyId(String policyId);
}

