package zw.gov.mohcc.impilo.auditledger.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.auditledger.domain.ChainHeadEntity;
import java.util.UUID;

public interface ChainHeadRepository extends JpaRepository<ChainHeadEntity, UUID> {
}
