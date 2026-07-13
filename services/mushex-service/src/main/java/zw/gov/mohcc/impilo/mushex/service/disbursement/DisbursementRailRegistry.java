package zw.gov.mohcc.impilo.mushex.service.disbursement;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.mushex.domain.enums.AdapterType;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of live {@link DisbursementRail} implementations, keyed by rail type.
 * A payout batch whose rail is not registered stays PENDING — fail-closed;
 * money is never fake-disbursed by a missing rail.
 */
@Component
public class DisbursementRailRegistry {

    private static final Logger log = LoggerFactory.getLogger(DisbursementRailRegistry.class);

    private final Map<AdapterType, DisbursementRail> rails = new EnumMap<>(AdapterType.class);

    public DisbursementRailRegistry(List<DisbursementRail> discovered) {
        for (DisbursementRail rail : discovered) {
            rails.put(rail.railType(), rail);
        }
        log.info("Disbursement rails registered: {}", rails.keySet());
    }

    public Optional<DisbursementRail> railFor(AdapterType type) {
        return Optional.ofNullable(rails.get(type));
    }
}
