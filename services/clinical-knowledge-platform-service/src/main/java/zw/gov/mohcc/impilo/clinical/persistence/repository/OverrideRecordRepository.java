package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.OverrideRecordEntity;

import java.util.UUID;

public interface OverrideRecordRepository extends JpaRepository<OverrideRecordEntity, UUID> {
}
