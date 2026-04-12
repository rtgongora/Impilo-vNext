package zw.gov.mohcc.impilo.coverage.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;
import zw.gov.mohcc.impilo.coverage.domain.AppealEntity;

import java.util.UUID;

@Repository
public interface AppealRepository extends JpaRepository<AppealEntity, UUID>, JpaSpecificationExecutor<AppealEntity> {
}
