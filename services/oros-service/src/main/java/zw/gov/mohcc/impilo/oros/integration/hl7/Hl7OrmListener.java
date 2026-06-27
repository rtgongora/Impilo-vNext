package zw.gov.mohcc.impilo.oros.integration.hl7;

import ca.uhn.hl7v2.HapiContext;
import ca.uhn.hl7v2.app.HL7Service;
import ca.uhn.hl7v2.protocol.ReceivingApplication;
import ca.uhn.hl7v2.protocol.ReceivingApplicationException;
import ca.uhn.hl7v2.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.oros.core.ExternalOrderIntake;
import zw.gov.mohcc.impilo.oros.core.ExternalOrderIntakeService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Map;
import java.util.UUID;

/**
 * HL7 v2 ORM-inbound adapter: an MLLP listener that accepts {@code ORM^O01} new-order messages and
 * creates OROS orders via the shared {@link ExternalOrderIntakeService}.
 *
 * <p>Started only when {@code oros.integration.hl7.orm-inbound.enabled=true} (so no socket is
 * opened by default — the honest NOT_LIVE seam). The MLLP endpoint is provisioned per tenant +
 * facility (config), so a synthetic system {@link TrustContext} is established for each inbound
 * message (there are no trust headers on an MLLP feed).</p>
 */
@Component
@ConditionalOnProperty(name = "oros.integration.hl7.orm-inbound.enabled", havingValue = "true")
public class Hl7OrmListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(Hl7OrmListener.class);

    private final HapiContext hapiContext;
    private final Hl7OrmMapper mapper;
    private final ExternalOrderIntakeService intakeService;
    private final int port;
    private final UUID tenantId;
    private final UUID facilityId;

    private HL7Service server;
    private volatile boolean running;

    public Hl7OrmListener(HapiContext hapiContext, Hl7OrmMapper mapper,
                          ExternalOrderIntakeService intakeService,
                          @Value("${oros.integration.hl7.orm-inbound.port:2575}") int port,
                          @Value("${oros.integration.hl7.orm-inbound.tenant-id:}") String tenantId,
                          @Value("${oros.integration.hl7.orm-inbound.facility-id:}") String facilityId) {
        this.hapiContext = hapiContext;
        this.mapper = mapper;
        this.intakeService = intakeService;
        this.port = port;
        this.tenantId = parseUuid(tenantId);
        this.facilityId = parseUuid(facilityId);
    }

    @Override
    public void start() {
        if (tenantId == null || facilityId == null) {
            log.warn("HL7 ORM-inbound enabled but tenant-id/facility-id not configured — listener not started");
            return;
        }
        try {
            server = hapiContext.newServer(port, false);
            server.registerApplication("ORM", "O01", new OrmReceivingApplication());
            server.startAndWait();
            running = true;
            log.info("HL7 ORM-inbound MLLP listener started on port {} (tenant={}, facility={})",
                    port, tenantId, facilityId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("HL7 ORM-inbound listener start interrupted: {}", e.getMessage());
        } catch (Exception e) {
            log.error("HL7 ORM-inbound listener failed to start on port {}: {}", port, e.getMessage(), e);
        }
    }

    @Override
    public void stop() {
        running = false;
        if (server != null) {
            server.stopAndWait();
            log.info("HL7 ORM-inbound MLLP listener stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** Start after the web context is up. */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }

    private final class OrmReceivingApplication implements ReceivingApplication<Message> {
        @Override
        public Message processMessage(Message message, Map<String, Object> metadata)
                throws ReceivingApplicationException {
            try {
                String raw = hapiContext.getPipeParser().encode(message);
                ExternalOrderIntake input = mapper.parse(raw);

                TrustContextHolder.set(new TrustContext(tenantId, "hl7:orm-inbound", "SYSTEM",
                        "TREATMENT", null, UUID.randomUUID(), facilityId, null, null, AccessMode.INTERNAL));
                try {
                    var order = intakeService.create(input);
                    log.info("HL7 ORM^O01 ingested as orderId={}", order.getOrderId());
                } finally {
                    TrustContextHolder.clear();
                }
                return message.generateACK();
            } catch (Exception e) {
                log.warn("Failed to process inbound ORM^O01: {}", e.getMessage());
                try {
                    return message.generateACK(ca.uhn.hl7v2.AcknowledgmentCode.AE, new ca.uhn.hl7v2.HL7Exception(e.getMessage()));
                } catch (Exception ackError) {
                    throw new ReceivingApplicationException(ackError);
                }
            }
        }

        @Override
        public boolean canProcess(Message message) {
            return true;
        }
    }

    private static UUID parseUuid(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(s.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
