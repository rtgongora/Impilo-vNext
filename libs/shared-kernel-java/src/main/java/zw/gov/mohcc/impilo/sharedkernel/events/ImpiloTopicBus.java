package zw.gov.mohcc.impilo.sharedkernel.events;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Bus separation registry for Impilo v1.1 Kafka topics.
 *
 * <h3>Bus prefix convention</h3>
 * <ul>
 *   <li>{@code clinical.*} — Ring 1 clinical services (PCT, OROS, Pharmacy, Inpatient)</li>
 *   <li>{@code kernel.*}   — Ring 0 infrastructure services (VITO, TUSO, VARAPI, MSIKA, ZIBO, UBOMI, MUSHEX, COSTA)</li>
 *   <li>{@code trust.*}    — TSHEPO trust plane (revocation, audit, identity, consent, offline)</li>
 *   <li>{@code telemetry.*} — Observability and metrics</li>
 *   <li>{@code analytics.*} — Analytical / reporting pipeline</li>
 * </ul>
 *
 * <p>Use {@link #busFor(String)} to determine which bus a topic belongs to,
 * and {@link #assertBus(String, String)} for CI-time enforcement.</p>
 */
public final class ImpiloTopicBus {

    public static final String CLINICAL = "clinical";
    public static final String KERNEL = "kernel";
    public static final String TRUST = "trust";
    public static final String TELEMETRY = "telemetry";
    public static final String ANALYTICS = "analytics";
    public static final String UNKNOWN = "unknown";

    /**
     * All known buses in declaration order (evaluated first-match against topic prefix).
     */
    private static final List<String> BUSES = List.of(CLINICAL, KERNEL, TRUST, TELEMETRY, ANALYTICS);

    /**
     * Canonical bus assignment for every v1.1 topic emitted by Impilo services.
     * Key = full topic name, Value = bus constant.
     */
    public static final Map<String, String> TOPIC_BUS_MAP = Map.ofEntries(
            // ── PCT (clinical bus) ──
            Map.entry("clinical.pct.journey.state_changed", CLINICAL),
            Map.entry("clinical.pct.queue.item_changed", CLINICAL),
            Map.entry("clinical.pct.encounter.started", CLINICAL),
            Map.entry("clinical.pct.encounter.completed", CLINICAL),
            Map.entry("clinical.pct.admission.updated", CLINICAL),
            Map.entry("clinical.pct.discharge.started", CLINICAL),
            Map.entry("clinical.pct.discharge.completed", CLINICAL),
            Map.entry("clinical.pct.death.recorded", CLINICAL),
            Map.entry("clinical.pct.death.completed", CLINICAL),
            Map.entry("clinical.pct.task.updated", CLINICAL),
            Map.entry("clinical.pct.triage.recorded", CLINICAL),
            Map.entry("clinical.pct.transfer.updated", CLINICAL),
            Map.entry("clinical.pct.events", CLINICAL),

            // ── OROS (clinical bus) ──
            Map.entry("clinical.oros.order.placed", CLINICAL),
            Map.entry("clinical.oros.order.status_changed", CLINICAL),
            Map.entry("clinical.oros.order.routed", CLINICAL),
            Map.entry("clinical.oros.order.cancelled", CLINICAL),
            Map.entry("clinical.oros.workstep.changed", CLINICAL),
            Map.entry("clinical.oros.result.available", CLINICAL),
            Map.entry("clinical.oros.result.critical", CLINICAL),
            Map.entry("clinical.oros.ack.received", CLINICAL),
            Map.entry("clinical.oros.ack.escalation", CLINICAL),
            Map.entry("clinical.oros.sla.breached", CLINICAL),
            Map.entry("clinical.oros.reconcile.updated", CLINICAL),
            Map.entry("clinical.oros.events", CLINICAL),

            // ── Pharmacy (clinical bus) ──
            Map.entry("clinical.pharmacy.dispense.accepted", CLINICAL),
            Map.entry("clinical.pharmacy.dispense.partial", CLINICAL),
            Map.entry("clinical.pharmacy.dispense.completed", CLINICAL),
            Map.entry("clinical.pharmacy.dispense.backordered", CLINICAL),
            Map.entry("clinical.pharmacy.dispense.cancelled", CLINICAL),
            Map.entry("clinical.pharmacy.stock.movement_recorded", CLINICAL),
            Map.entry("clinical.pharmacy.reversal.completed", CLINICAL),
            Map.entry("clinical.pharmacy.pickup.claimed", CLINICAL),
            Map.entry("clinical.pharmacy.mushex.charge", CLINICAL),
            Map.entry("clinical.pharmacy.reconcile.updated", CLINICAL),
            Map.entry("clinical.pharmacy.events", CLINICAL),

            // ── Inpatient (clinical bus) ──
            Map.entry("clinical.inpatient.admission.created", CLINICAL),
            Map.entry("clinical.inpatient.admission.transferred", CLINICAL),
            Map.entry("clinical.inpatient.admission.discharged", CLINICAL),
            Map.entry("clinical.inpatient.admission.death_discharge", CLINICAL),
            Map.entry("clinical.inpatient.bed.assigned", CLINICAL),
            Map.entry("clinical.inpatient.bed.released", CLINICAL),
            Map.entry("clinical.inpatient.ward.updated", CLINICAL),
            Map.entry("clinical.inpatient.events", CLINICAL),

            // ── VITO (kernel bus) ──
            Map.entry("kernel.vito.identity", KERNEL),
            Map.entry("kernel.vito.alias", KERNEL),
            Map.entry("kernel.vito.cards", KERNEL),
            Map.entry("kernel.vito.wallet", KERNEL),
            Map.entry("kernel.vito.issuance", KERNEL),
            Map.entry("kernel.vito.dedup", KERNEL),
            Map.entry("kernel.vito.offline", KERNEL),
            Map.entry("kernel.vito.pickup", KERNEL),
            Map.entry("kernel.vito.print", KERNEL),
            Map.entry("kernel.vito.events", KERNEL),

            // ── TUSO (kernel bus) ──
            Map.entry("kernel.tuso.facility", KERNEL),
            Map.entry("kernel.tuso.service_area", KERNEL),
            Map.entry("kernel.tuso.resource", KERNEL),
            Map.entry("kernel.tuso.events", KERNEL),

            // ── VARAPI (kernel bus) ──
            Map.entry("kernel.varapi.provider", KERNEL),
            Map.entry("kernel.varapi.council", KERNEL),
            Map.entry("kernel.varapi.credential", KERNEL),
            Map.entry("kernel.varapi.events", KERNEL),

            // ── MSIKA (kernel bus) ──
            Map.entry("kernel.msika.catalog", KERNEL),
            Map.entry("kernel.msika.item", KERNEL),
            Map.entry("kernel.msika.mapping", KERNEL),
            Map.entry("kernel.msika.tariff", KERNEL),
            Map.entry("kernel.msika.events", KERNEL),

            // ── ZIBO (kernel bus) ──
            Map.entry("kernel.zibo.artifact", KERNEL),
            Map.entry("kernel.zibo.pack", KERNEL),
            Map.entry("kernel.zibo.validation", KERNEL),
            Map.entry("kernel.zibo.events", KERNEL),

            // ── UBOMI (kernel bus) ──
            Map.entry("kernel.ubomi.birth", KERNEL),
            Map.entry("kernel.ubomi.death", KERNEL),
            Map.entry("kernel.ubomi.events", KERNEL),

            // ── MUSHEX (kernel bus) ──
            Map.entry("kernel.mushex.payment.intent.created", KERNEL),
            Map.entry("kernel.mushex.payment.status.changed", KERNEL),
            Map.entry("kernel.mushex.payment.refund.changed", KERNEL),
            Map.entry("kernel.mushex.payment.settlement.released", KERNEL),
            Map.entry("kernel.mushex.payment.fraud.flagged", KERNEL),
            Map.entry("kernel.mushex.payment.remittance.issued", KERNEL),
            Map.entry("kernel.mushex.payment.remittance.claimed", KERNEL),
            Map.entry("kernel.mushex.claim.submitted", KERNEL),
            Map.entry("kernel.mushex.claim.adjudicated", KERNEL),
            Map.entry("kernel.mushex.claim.paid", KERNEL),
            Map.entry("kernel.mushex.claim.disputed", KERNEL),
            Map.entry("kernel.mushex.events", KERNEL),

            // ── COSTA (kernel bus) ──
            Map.entry("kernel.costa.bill.draft_created", KERNEL),
            Map.entry("kernel.costa.bill.approval_requested", KERNEL),
            Map.entry("kernel.costa.bill.approved", KERNEL),
            Map.entry("kernel.costa.bill.finalized", KERNEL),
            Map.entry("kernel.costa.bill.voided", KERNEL),
            Map.entry("kernel.costa.invoice.issued", KERNEL),
            Map.entry("kernel.costa.payment.intent_created", KERNEL),
            Map.entry("kernel.costa.payment.status_changed", KERNEL),
            Map.entry("kernel.costa.refund.issued", KERNEL),
            Map.entry("kernel.costa.claim.pack_created", KERNEL),
            Map.entry("kernel.costa.estimate.created", KERNEL),
            Map.entry("kernel.costa.ruleset.published", KERNEL),
            Map.entry("kernel.costa.events", KERNEL),

            // ── TSHEPO trust plane (trust bus) ──
            Map.entry("trust.revocation.consent", TRUST),
            Map.entry("trust.revocation.privilege", TRUST),
            Map.entry("trust.revocation.identity", TRUST),
            Map.entry("trust.federation.merge", TRUST),
            Map.entry("trust.federation.pod_registered", TRUST),
            Map.entry("trust.decision_evidence", TRUST),
            Map.entry("trust.tshepo.audit.events", TRUST),
            Map.entry("trust.tshepo.identity.events", TRUST),
            Map.entry("trust.tshepo.offline.events", TRUST),
            Map.entry("trust.tshepo.keys.events", TRUST),
            Map.entry("trust.tshepo.consent.events", TRUST)
    );

    private ImpiloTopicBus() {
    }

    /**
     * Determine the bus for a given topic by prefix matching.
     *
     * @param topic the Kafka topic name
     * @return one of the bus constants, or {@link #UNKNOWN} if unrecognized
     */
    public static String busFor(String topic) {
        if (topic == null) return UNKNOWN;
        for (String bus : BUSES) {
            if (topic.startsWith(bus + ".")) {
                return bus;
            }
        }
        return UNKNOWN;
    }

    /**
     * Assert that a topic belongs to the expected bus.
     *
     * @param expectedBus the bus constant (e.g. {@link #CLINICAL}, {@link #KERNEL})
     * @param topic       the Kafka topic name to validate
     * @throws IllegalArgumentException if the topic does not belong to the expected bus
     */
    public static void assertBus(String expectedBus, String topic) {
        Objects.requireNonNull(expectedBus, "expectedBus is required");
        Objects.requireNonNull(topic, "topic is required");
        String actual = busFor(topic);
        if (!expectedBus.equals(actual)) {
            throw new IllegalArgumentException(
                    "Bus violation: topic '" + topic + "' belongs to bus '" + actual
                    + "' but expected '" + expectedBus + "'");
        }
    }

    /**
     * Return all topics registered for a specific bus.
     *
     * @param bus the bus constant
     * @return set of topic names assigned to that bus
     */
    public static Set<String> topicsForBus(String bus) {
        return TOPIC_BUS_MAP.entrySet().stream()
                .filter(e -> bus.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
