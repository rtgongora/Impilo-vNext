package zw.gov.mohcc.impilo.msikaflow.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.msikaflow.domain.ReviewStatus;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.OpsReviewEntity;

import java.util.UUID;

@Repository
public interface OpsReviewRepository extends JpaRepository<OpsReviewEntity, String> {
    Page<OpsReviewEntity> findByStatusAndTenantId(ReviewStatus status, UUID tenantId, Pageable pageable);
}
