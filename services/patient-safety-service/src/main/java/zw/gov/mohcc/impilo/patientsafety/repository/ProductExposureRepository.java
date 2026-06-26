package zw.gov.mohcc.impilo.patientsafety.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.patientsafety.domain.ProductExposureEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProductExposureRepository extends JpaRepository<ProductExposureEntity, UUID> {
    List<ProductExposureEntity> findByReportIdOrderByCreatedAtAsc(UUID reportId);
}
