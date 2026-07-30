package zw.gov.mohcc.impilo.varapi.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.varapi.core.RegisterMaterialisationService;

import java.util.Locale;
import java.util.UUID;

/**
 * Reconciles a council's registers when its regulatory configuration is activated (task #97).
 *
 * <p>Activation is the moment a council's approved configuration becomes the one that governs, so
 * it is the moment its registers should match it. The operator endpoint
 * ({@code POST /v1/internal/councils/{councilId}/registers/reconcile}) does the same work on
 * demand; this makes it automatic. The reconcile is idempotent, so a redelivered event is safe and
 * an operator running the endpoint after an event has already fired writes nothing.</p>
 *
 * <p><b>Only an activation.</b> A release reaching DRAFT, IN_REVIEW or APPROVED emits nothing this
 * listener acts on. That is rule 4 of the materialiser — a council's registers follow approval,
 * never authoring.</p>
 *
 * <p><b>Where this listener is live, and where it is not.</b> org-registry publishes to Kafka only
 * when {@code impilo.orgregistry.kafka-events-enabled=true}
 * ({@code ORG_REGISTRY_KAFKA_EVENTS_ENABLED}). The code default is {@code false}, and in that mode
 * {@code OrgRegistryOutboxNoKafkaDrainer} marks outbox rows published and discards them, so
 * activation reconciles nothing and the operator endpoint is the only path. The full-preview estate
 * sets the flag to {@code true} ({@code deploy/helm/.../values-full-preview-runtime.generated.yaml}),
 * verified on the running pod — so this listener is live there, not inert. Local, CI and any
 * environment without a broker run on the default and are not.</p>
 *
 * <p><b>What has never happened.</b> As at 2026-07-30 the preview {@code event_outbox} holds zero
 * rows: no configuration release has been activated and no appointment ended on that estate, so no
 * org-registry event has ever been published there by any producer. The flag being on is not
 * evidence the path works; nothing has yet travelled it. "No event arrived" and "no release was
 * activated" are indistinguishable from here, which is why the first activation after deploy must be
 * checked against the reconcile it should cause rather than assumed. The same applies to
 * {@code RegulatoryAppointmentEndedTokenConsumer} in tshepo-identity.</p>
 */
@Component
public class ConfigReleaseActivatedListener {

    private static final Logger log = LoggerFactory.getLogger(ConfigReleaseActivatedListener.class);

    private static final String AGGREGATE_TYPE = "REGULATORY_CONFIG_RELEASE";

    private final ObjectMapper objectMapper;
    private final RegisterMaterialisationService materialisationService;

    public ConfigReleaseActivatedListener(ObjectMapper objectMapper,
                                          RegisterMaterialisationService materialisationService) {
        this.objectMapper = objectMapper;
        this.materialisationService = materialisationService;
    }

    @KafkaListener(
            topics = {"impilo.org-registry.events", "org-registry.events"},
            groupId = "varapi-register-materialisation")
    public void onOrgRegistryEvent(String message) {
        try {
            if (message == null || message.isBlank()) {
                return;
            }
            JsonNode root = objectMapper.readTree(message);

            // OrgRegistryOutboxPublisher writes a camelCase envelope (eventType / aggregateType /
            // tenantId) around the payload. Reading a snake_case key here — the shape vashandi's
            // publisher uses — would compile, run, match nothing, and look exactly like a quiet
            // estate. The envelope shape is asserted in the test for that reason.
            if (!AGGREGATE_TYPE.equalsIgnoreCase(text(root, "aggregateType"))) {
                return;
            }
            String eventType = text(root, "eventType").toLowerCase(Locale.ROOT);
            if (!eventType.contains("activated")) {
                return;
            }

            JsonNode payload = root.path("payload").isObject() ? root.path("payload") : root;
            UUID tenantId = uuid(text(root, "tenantId"));
            UUID organisationId = uuid(text(payload, "organisationId"));
            if (tenantId == null || organisationId == null) {
                // Never reconcile on a guess: without both anchors we cannot tell whose registers
                // this concerns, and reconciling the wrong council could retire real registers.
                log.warn("Configuration release activated without a tenant/organisation anchor — "
                                + "registers not reconciled: releaseId={}",
                        text(payload, "releaseId"));
                return;
            }

            materialisationService.reconcileForOrganisation(tenantId, organisationId,
                            "system:config-release-activated")
                    .ifPresentOrElse(
                            report -> log.info("Configuration release {} activated: reconciled {} "
                                            + "registers for {} (created={} updated={} unchanged={} "
                                            + "retired={} reinstated={})",
                                    text(payload, "releaseId"), report.declared(),
                                    report.councilCode(), report.created(), report.updated(),
                                    report.unchanged(), report.retired(), report.reinstated()),
                            () -> log.debug("Configuration release {} activated for organisation {}, "
                                            + "which regulates no council in varapi — no registers "
                                            + "to reconcile",
                                    text(payload, "releaseId"), organisationId));
        } catch (Exception e) {
            // A poisoned message must not stall the partition; the endpoint remains the recovery
            // path and the reconcile is idempotent, so nothing is lost by dropping this delivery.
            log.error("Failed to reconcile registers from an org-registry configuration event: {}",
                    e.getMessage(), e);
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) {
            return "";
        }
        return node.get(field).asText("").trim();
    }

    private static UUID uuid(String value) {
        try {
            return value == null || value.isBlank() ? null : UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
