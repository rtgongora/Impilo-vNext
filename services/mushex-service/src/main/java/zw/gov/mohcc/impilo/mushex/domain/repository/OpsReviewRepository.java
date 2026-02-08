package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.OpsReviewEntity;
import zw.gov.mohcc.impilo.mushex.domain.enums.ReviewStatus;

import java.util.UUID;

@Repository
public interface OpsReviewRepository extends JpaRepository<OpsReviewEntity, String> {

    Page<OpsReviewEntity> findByTenantIdAndStatus(UUID tenantId, ReviewStatus status, Pageable pageable);
}
