package zw.gov.mohcc.impilo.oros.core;

import zw.gov.mohcc.impilo.oros.domain.OrderPriority;
import zw.gov.mohcc.impilo.oros.domain.OrderType;

/**
 * Normalized input for an externally-originated order (FHIR ServiceRequest-inbound, HL7 ORM-inbound).
 *
 * @param patientCpid  resolved Impilo CPID
 * @param orderType    LAB / IMAGING / …
 * @param priority     mapped priority (defaults ROUTINE upstream)
 * @param code         orderable/procedure code
 * @param displayName  human-readable code label (may equal {@code code})
 * @param clinicalNotes optional notes / reason
 * @param externalRef  idempotency key from the external system (system|value), nullable
 */
public record ExternalOrderIntake(
        String patientCpid,
        OrderType orderType,
        OrderPriority priority,
        String code,
        String displayName,
        String clinicalNotes,
        String externalRef
) {}
