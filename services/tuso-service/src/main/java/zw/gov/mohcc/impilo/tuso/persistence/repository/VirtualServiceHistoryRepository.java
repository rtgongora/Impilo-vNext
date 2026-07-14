package zw.gov.mohcc.impilo.tuso.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.tuso.persistence.entity.VirtualServiceHistoryEntity;

import java.util.List;

@Repository
public interface VirtualServiceHistoryRepository extends JpaRepository<VirtualServiceHistoryEntity, Long> {

    List<VirtualServiceHistoryEntity> findTop100ByVirtualServiceIdOrderByChangedAtDesc(Long virtualServiceId);
}
