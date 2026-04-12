package zw.gov.mohcc.impilo.simba.core;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeEntity;
import zw.gov.mohcc.impilo.simba.persistence.entity.ChallengeParticipantEntity;
import zw.gov.mohcc.impilo.simba.persistence.repository.ChallengeParticipantRepository;
import zw.gov.mohcc.impilo.simba.persistence.repository.ChallengeRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeParticipantRepository participantRepository;

    public ChallengeService(ChallengeRepository challengeRepository,
                            ChallengeParticipantRepository participantRepository) {
        this.challengeRepository = challengeRepository;
        this.participantRepository = participantRepository;
    }

    @Transactional
    public ChallengeEntity create(ChallengeEntity challenge) {
        return challengeRepository.save(challenge);
    }

    @Transactional(readOnly = true)
    public List<ChallengeEntity> list(UUID tenantId) {
        return challengeRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    @Transactional
    public ChallengeParticipantEntity join(UUID challengeId, UUID tenantId, String personCpid) {
        ChallengeEntity challenge = challengeRepository.findByChallengeIdAndTenantId(challengeId, tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Challenge not found"));
        if (participantRepository.findByChallengeIdAndPersonCpid(challenge.getChallengeId(), personCpid)
                .isPresent()) {
            throw new IllegalStateException("Already participating");
        }
        ChallengeParticipantEntity p = new ChallengeParticipantEntity();
        p.setTenantId(tenantId);
        p.setChallengeId(challenge.getChallengeId());
        p.setPersonCpid(personCpid);
        return participantRepository.save(p);
    }

    @Transactional
    public ChallengeParticipantEntity updateProgress(UUID challengeId, String personCpid, BigDecimal progress) {
        ChallengeParticipantEntity p = participantRepository
                .findByChallengeIdAndPersonCpid(challengeId, personCpid)
                .orElseThrow(() -> new IllegalArgumentException("Participant not found"));
        p.setProgressValue(progress);
        return participantRepository.save(p);
    }
}
