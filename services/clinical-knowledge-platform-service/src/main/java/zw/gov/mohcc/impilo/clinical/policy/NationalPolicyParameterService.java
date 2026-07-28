package zw.gov.mohcc.impilo.clinical.policy;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.clinical.persistence.entity.NationalPolicyParameterEntity;
import zw.gov.mohcc.impilo.clinical.persistence.repository.NationalPolicyParameterRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reads governed policy parameters as they stood on a given date.
 *
 * <p>Deliberately returns the parameter WITH its approval and verification status rather than a bare
 * value. A consumer must be able to tell a ratified national policy from an engineering seed, because
 * the two justify different behaviour: the RMNP confidentiality stamper, for instance, will read a
 * seeded age and still decline to apply a protection class from it.
 */
@Service
public class NationalPolicyParameterService {

    private final NationalPolicyParameterRepository parameters;

    public NationalPolicyParameterService(NationalPolicyParameterRepository parameters) {
        this.parameters = parameters;
    }

    /** The version in force on {@code asOf}; empty when no version covers that date. */
    @Transactional(readOnly = true)
    public Optional<NationalPolicyParameterEntity> inForce(String code, LocalDate asOf) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        LocalDate on = asOf == null ? LocalDate.now() : asOf;
        List<NationalPolicyParameterEntity> found = parameters.findInForce(code, on);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }
}
