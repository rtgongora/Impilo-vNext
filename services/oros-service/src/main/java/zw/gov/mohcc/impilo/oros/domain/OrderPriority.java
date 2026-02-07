package zw.gov.mohcc.impilo.oros.domain;

/**
 * Priority classification for clinical orders.
 *
 * <p>Maps to HL7 FHIR RequestPriority and drives SLA timer targets
 * and queue ordering within fulfillment departments.</p>
 * <ul>
 *   <li>{@code ROUTINE} — standard processing, default TAT applies</li>
 *   <li>{@code URGENT} — elevated priority, reduced TAT</li>
 *   <li>{@code STAT} — immediate processing required (emergency)</li>
 *   <li>{@code ASAP} — as soon as possible, between urgent and stat</li>
 * </ul>
 */
public enum OrderPriority {
    ROUTINE,
    URGENT,
    STAT,
    ASAP
}
