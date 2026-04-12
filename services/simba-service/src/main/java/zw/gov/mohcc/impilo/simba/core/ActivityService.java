package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.ActivityEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.ActivityRepository;

import java.util.List;
import java.util.UUID;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;

    public ActivityService(ActivityRepository activityRepository) {
        this.activityRepository = activityRepository;
    }

    @Transactional
    public ActivityEntity record(ActivityEntity entity) {
        return activityRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<ActivityEntity> list(UUID tenantId, String personCpid, String activityType) {
        if (activityType != null && !activityType.isBlank()) {
            return activityRepository.findByTenantIdAndPersonCpidAndActivityTypeOrderByRecordedAtDesc(
                    tenantId, personCpid, activityType);
        }
        return activityRepository.findByTenantIdAndPersonCpidOrderByRecordedAtDesc(tenantId, personCpid);
    }
}
