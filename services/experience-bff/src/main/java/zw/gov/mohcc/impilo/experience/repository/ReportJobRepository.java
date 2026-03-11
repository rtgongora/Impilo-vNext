package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.ReportJob;

import java.util.UUID;

public interface ReportJobRepository extends JpaRepository<ReportJob, UUID> {
}
