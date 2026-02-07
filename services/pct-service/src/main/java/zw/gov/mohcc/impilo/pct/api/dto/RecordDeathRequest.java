package zw.gov.mohcc.impilo.pct.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * Request to record a patient death within a journey.
 *
 * <p>Captures the details of the death pronouncement including
 * the pronouncing clinician and the time of death. This triggers
 * the death workflow including UBOMI (civil registry) notification.</p>
 *
 * @param pronouncedBy the identifier of the clinician who pronounced death
 * @param pronouncedAt the date/time when death was pronounced
 */
public record RecordDeathRequest(
        @NotBlank String pronouncedBy,
        @NotNull OffsetDateTime pronouncedAt
) {}
