package zw.gov.mohcc.impilo.ndila.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.ndila.domain.NdilaProviderRequestEntity;

@Repository
public interface NdilaProviderRequestRepository extends JpaRepository<NdilaProviderRequestEntity, Long> {
}
