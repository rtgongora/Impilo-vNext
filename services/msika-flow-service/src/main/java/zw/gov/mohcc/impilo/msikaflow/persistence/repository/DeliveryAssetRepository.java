package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.DeliveryAssetEntity;

import java.util.List;

@Repository
public interface DeliveryAssetRepository extends JpaRepository<DeliveryAssetEntity, String> {
    List<DeliveryAssetEntity> findByProviderIdAndActiveOrderByUpdatedAtDesc(String providerId, boolean active);
}

