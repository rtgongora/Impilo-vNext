package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.SleepSegmentEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.SleepSegmentRepository;

import java.util.List;
import java.util.UUID;

@Service
public class SleepService {

    private final SleepSegmentRepository sleepSegmentRepository;

    public SleepService(SleepSegmentRepository sleepSegmentRepository) {
        this.sleepSegmentRepository = sleepSegmentRepository;
    }

    @Transactional
    public SleepSegmentEntity record(SleepSegmentEntity entity) {
        return sleepSegmentRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<SleepSegmentEntity> list(UUID tenantId, String personCpid) {
        return sleepSegmentRepository.findByTenantIdAndPersonCpidOrderByStartTimeDesc(tenantId, personCpid);
    }
}
