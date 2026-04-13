package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.VitalsRecord;

import java.util.List;
import java.util.UUID;

public interface VitalsRecordRepository extends JpaRepository<VitalsRecord, UUID> {

    List<VitalsRecord> findByEncounterId(UUID encounterId);

    List<VitalsRecord> findByPatientIdOrderByRecordedAtDesc(UUID patientId);

    Page<VitalsRecord> findByTenantIdAndPatientId(String tenantId, UUID patientId, Pageable pageable);
}
