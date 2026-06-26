package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.pct.persistence.entity.CadreDecisionEntity;

import java.util.UUID;

public interface CadreDecisionRepository extends JpaRepository<CadreDecisionEntity, UUID> {
}
