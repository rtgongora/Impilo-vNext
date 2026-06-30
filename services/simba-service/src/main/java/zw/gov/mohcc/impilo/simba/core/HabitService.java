package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.HabitCheckInEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.HabitCheckInRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight daily habit check-ins. Idempotent per (person, habit, day): a second check-in for
 * the same day updates the existing row rather than creating a duplicate.
 */
@Service
public class HabitService {

    private final HabitCheckInRepository repository;

    public HabitService(HabitCheckInRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<HabitCheckInEntity> list(UUID tenantId, String personCpid) {
        return repository.findByTenantIdAndPersonCpidOrderByCheckInDateDesc(tenantId, personCpid);
    }

    @Transactional
    public HabitCheckInEntity checkIn(HabitCheckInEntity incoming) {
        LocalDate day = incoming.getCheckInDate() != null ? incoming.getCheckInDate() : LocalDate.now();
        HabitCheckInEntity existing = repository
                .findByTenantIdAndPersonCpidAndHabitCodeAndCheckInDate(
                        incoming.getTenantId(), incoming.getPersonCpid(), incoming.getHabitCode(), day)
                .orElse(null);
        if (existing != null) {
            existing.setStatus(incoming.getStatus() != null ? incoming.getStatus() : "DONE");
            existing.setNote(incoming.getNote());
            if (incoming.getGoalId() != null) {
                existing.setGoalId(incoming.getGoalId());
            }
            if (incoming.getEnrollmentId() != null) {
                existing.setEnrollmentId(incoming.getEnrollmentId());
            }
            return repository.save(existing);
        }
        incoming.setCheckInDate(day);
        return repository.save(incoming);
    }
}
