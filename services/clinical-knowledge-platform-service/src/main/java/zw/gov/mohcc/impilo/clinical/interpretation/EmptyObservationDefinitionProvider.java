package zw.gov.mohcc.impilo.clinical.interpretation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.clinical.interpretation.model.QualifiedInterval;

import java.util.List;

/**
 * Fail-closed default {@link ObservationDefinitionProvider}: returns no governed intervals, so vitals/
 * standard analytes resolve to {@code NO_REFERENCE_RANGE} (never a fabricated "normal") until the
 * ZIBO-backed provider is wired and seeded. Lab-delivered intervals are unaffected — they are carried on
 * the observation itself and resolved without this provider.
 *
 * <p>Backs off automatically once a concrete {@link ObservationDefinitionProvider} bean (e.g. the
 * ZIBO-backed one) is present.</p>
 */
@Component
@ConditionalOnMissingBean(ObservationDefinitionProvider.class)
public class EmptyObservationDefinitionProvider implements ObservationDefinitionProvider {

    private static final Logger log = LoggerFactory.getLogger(EmptyObservationDefinitionProvider.class);

    public EmptyObservationDefinitionProvider() {
        log.info("No governed ObservationDefinition provider configured — vitals/standard analytes will "
                + "resolve to NO_REFERENCE_RANGE (fail-closed). Lab-delivered intervals still apply.");
    }

    @Override
    public List<QualifiedInterval> findIntervals(String loincCode, String localCode) {
        return List.of();
    }
}
