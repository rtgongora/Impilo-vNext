package zw.gov.mohcc.impilo.clinical.persistence.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.clinical.persistence.entity.ExtractionJobEntity;

import java.util.UUID;

public interface ExtractionJobRepository extends JpaRepository<ExtractionJobEntity, UUID> {
}
