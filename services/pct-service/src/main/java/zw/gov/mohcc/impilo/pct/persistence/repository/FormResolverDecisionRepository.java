package zw.gov.mohcc.impilo.pct.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.pct.persistence.entity.FormResolverDecisionEntity;

import java.util.UUID;

@Repository
public interface FormResolverDecisionRepository extends JpaRepository<FormResolverDecisionEntity, UUID> {
}
