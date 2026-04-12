package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.DietEntryEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.DietEntryRepository;

import java.util.List;
import java.util.UUID;

@Service
public class DietService {

    private final DietEntryRepository dietEntryRepository;

    public DietService(DietEntryRepository dietEntryRepository) {
        this.dietEntryRepository = dietEntryRepository;
    }

    @Transactional
    public DietEntryEntity record(DietEntryEntity entity) {
        return dietEntryRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<DietEntryEntity> list(UUID tenantId, String personCpid) {
        return dietEntryRepository.findByTenantIdAndPersonCpidOrderByRecordedAtDesc(tenantId, personCpid);
    }
}
