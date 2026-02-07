package zw.gov.mohcc.impilo.tshepo.offline.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.CapabilityTokenEntity;
import zw.gov.mohcc.impilo.tshepo.offline.persistence.CapabilityTokenRepository;

import java.util.List;

/**
 * Scheduled task that transitions expired ACTIVE capability tokens to EXPIRED status.
 * Runs every 5 minutes to ensure tokens are marked expired promptly.
 */
@Component
public class CapabilityTokenExpiryTask {

    private static final Logger log = LoggerFactory.getLogger(CapabilityTokenExpiryTask.class);

    private final CapabilityTokenRepository tokenRepo;

    public CapabilityTokenExpiryTask(CapabilityTokenRepository tokenRepo) {
        this.tokenRepo = tokenRepo;
    }

    @Scheduled(fixedDelayString = "${tshepo.offline.expiry-check-interval-ms:300000}")
    @Transactional
    public void expireTokens() {
        List<CapabilityTokenEntity> expiredTokens = tokenRepo.findExpiredActiveTokens();
        if (expiredTokens.isEmpty()) {
            return;
        }

        log.info("Expiring {} active tokens that have passed their TTL", expiredTokens.size());
        for (CapabilityTokenEntity token : expiredTokens) {
            token.setStatus("EXPIRED");
            tokenRepo.save(token);
        }
        log.info("Expired {} capability tokens", expiredTokens.size());
    }
}
