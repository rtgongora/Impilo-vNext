package zw.gov.mohcc.impilo.experience.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import zw.gov.mohcc.impilo.experience.domain.Reminder;

import java.util.Optional;
import java.util.UUID;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {

    Page<Reminder> findByTenantIdAndPatientIdOrderByScheduledAtDesc(String tenantId, UUID patientId, Pageable pageable);

    Optional<Reminder> findByIdAndTenantId(UUID id, String tenantId);
}
