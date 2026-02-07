package zw.gov.mohcc.impilo.inventory.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.inventory.domain.BinType;
import zw.gov.mohcc.impilo.inventory.persistence.entity.BinEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface BinRepository extends JpaRepository<BinEntity, UUID> {

    List<BinEntity> findByStoreId(UUID storeId);

    List<BinEntity> findByStoreIdAndBinType(UUID storeId, BinType binType);
}
