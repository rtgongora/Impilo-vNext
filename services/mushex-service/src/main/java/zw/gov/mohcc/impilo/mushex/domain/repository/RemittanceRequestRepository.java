package zw.gov.mohcc.impilo.mushex.domain.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.mushex.domain.entity.RemittanceRequestEntity;

import java.util.List;
import java.util.UUID;

@Repository
public interface RemittanceRequestRepository extends JpaRepository<RemittanceRequestEntity, String> {

    List<RemittanceRequestEntity> findByTenantIdAndSenderRefOrderByCreatedAtDesc(UUID tenantId, String senderRef);
}
