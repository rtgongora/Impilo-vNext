package zw.gov.mohcc.impilo.clinical.maternal;

import org.springframework.stereotype.Service;

/**
 * Stateless Bishop score assessment — cervical favourability for induction readiness.
 *
 * <p>Assessment-only: nothing is persisted here. The caller owns the encounter record.
 */
@Service
public class BishopScoreService {

    public BishopScoreEngine.Assessment assess(BishopScoreEngine.Input input) {
        return BishopScoreEngine.assess(input);
    }

    public boolean available() {
        return true;
    }
}
