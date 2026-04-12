package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.GoalEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.GoalRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class GoalService {

    private final GoalRepository goalRepository;

    public GoalService(GoalRepository goalRepository) {
        this.goalRepository = goalRepository;
    }

    @Transactional
    public GoalEntity create(GoalEntity entity) {
        return goalRepository.save(entity);
    }

    @Transactional(readOnly = true)
    public List<GoalEntity> list(UUID tenantId, String personCpid) {
        return goalRepository.findByTenantIdAndPersonCpidOrderByCreatedAtDesc(tenantId, personCpid);
    }

    @Transactional
    public GoalEntity updateProgress(Long id, UUID tenantId, BigDecimal currentValue) {
        GoalEntity goal = goalRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Goal not found"));
        goal.setCurrentValue(currentValue);
        return goalRepository.save(goal);
    }
}
