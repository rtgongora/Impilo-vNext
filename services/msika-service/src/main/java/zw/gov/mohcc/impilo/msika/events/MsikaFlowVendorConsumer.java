package zw.gov.mohcc.impilo.msika.events;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import zw.gov.mohcc.impilo.msika.api.dto.ListingDtos;
import zw.gov.mohcc.impilo.msika.api.dto.StorefrontDtos;
import zw.gov.mohcc.impilo.msika.core.ListingService;
import zw.gov.mohcc.impilo.msika.core.StorefrontService;
import zw.gov.mohcc.impilo.shared.auth.AccessMode;
import zw.gov.mohcc.impilo.shared.auth.TrustContext;
import zw.gov.mohcc.impilo.shared.auth.TrustContextHolder;

import java.util.Optional;
import java.util.UUID;

/**
 * Vendor ↔ storefront reconciliation. msika-flow owns vendor lifecycle truth;
 * this consumer keeps Msika's storefront lane consistent with it:
 *
 * <ul>
 *   <li><b>vendor approved</b> → ensure a VENDOR storefront exists (created
 *       UNVERIFIED — credential/licence verification stays a governed human
 *       action, never implied by flow approval);</li>
 *   <li><b>vendor suspended</b> → suspend the storefront and SUSPEND its
 *       PUBLISHED listings via the existing services (audited paths).</li>
 * </ul>
 *
 * Envelope-aware: accepts both bare payloads and v1.1 {payload:{...}} /
 * {data:{...}} envelopes (same idiom as msika-flow's PaymentEventConsumer).
 */
@Service
public class MsikaFlowVendorConsumer {

    static final String TOPIC_VENDOR_APPROVED = "msika.flow.vendor.approved";
    static final String TOPIC_VENDOR_SUSPENDED = "msika.flow.vendor.suspended";
    private static final String SELLER_TYPE_VENDOR = "VENDOR";

    private static final Logger log = LoggerFactory.getLogger(MsikaFlowVendorConsumer.class);

    private final StorefrontService storefrontService;
    private final ListingService listingService;
    private final ObjectMapper objectMapper;

    public MsikaFlowVendorConsumer(StorefrontService storefrontService,
                                   ListingService listingService,
                                   ObjectMapper objectMapper) {
        this.storefrontService = storefrontService;
        this.listingService = listingService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(
            topics = {TOPIC_VENDOR_APPROVED, TOPIC_VENDOR_SUSPENDED},
            groupId = "msika-service",
            autoStartup = "${SPRING_KAFKA_LISTENER_AUTO_STARTUP:true}")
    public void onVendorEvent(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            JsonNode root = objectMapper.readTree(message);
            JsonNode node = unwrapPayload(root);

            String vendorId = firstText(node, "vendorId", "sellerId", "aggregateId");
            if (vendorId == null) {
                log.warn("Vendor event on {} missing vendorId/sellerId — ignored", topic);
                return;
            }
            String vendorName = firstText(node, "vendorName", "displayName", "name", "legalName");
            UUID tenantId = parseUuid(firstText(node, "tenantId"));
            UUID correlationId = parseUuid(firstText(root, "correlationId"));

            // Kafka threads carry no HTTP trust headers; synthesize a system
            // TrustContext so the downstream services' audit rows attribute the
            // change to the event lane rather than failing on require().
            TrustContext ctx = new TrustContext(tenantId, "MSIKA_FLOW_EVENT", "SYSTEM",
                    "MARKETPLACE_SYNC", "kafka-consumer",
                    correlationId != null ? correlationId : UUID.randomUUID(),
                    null, null, null, AccessMode.INTERNAL);
            TrustContextHolder.set(ctx);
            try {
                if (TOPIC_VENDOR_APPROVED.equals(topic)) {
                    onVendorApproved(vendorId, vendorName, tenantId);
                } else if (TOPIC_VENDOR_SUSPENDED.equals(topic)) {
                    onVendorSuspended(vendorId);
                } else {
                    log.warn("Vendor event on unexpected topic {} — ignored", topic);
                }
            } finally {
                TrustContextHolder.clear();
            }
        } catch (Exception e) {
            log.error("Failed to process vendor event on {}: {}", topic, e.getMessage(), e);
        }
    }

    private void onVendorApproved(String vendorId, String vendorName, UUID tenantId) {
        Optional<StorefrontDtos.StorefrontView> existing =
                storefrontService.findBySeller(SELLER_TYPE_VENDOR, vendorId);
        if (existing.isPresent()) {
            log.info("Vendor {} approved — storefront {} already exists (verification={})",
                    vendorId, existing.get().storefrontId(), existing.get().verificationStatus());
            return;
        }
        StorefrontDtos.StorefrontView created = storefrontService.create(
                new StorefrontDtos.CreateStorefrontRequest(
                        tenantId, SELLER_TYPE_VENDOR, vendorId,
                        vendorName != null ? vendorName : "Vendor " + vendorId,
                        "Storefront created from msika-flow vendor approval; "
                                + "licence/credential verification pending.",
                        null, null, null));
        // Verification stays UNVERIFIED (entity default): flow approval is a
        // commercial decision, not a licence check.
        log.info("Vendor {} approved — created storefront {} (UNVERIFIED)",
                vendorId, created.storefrontId());
    }

    private void onVendorSuspended(String vendorId) {
        Optional<StorefrontDtos.StorefrontView> storefront =
                storefrontService.findBySeller(SELLER_TYPE_VENDOR, vendorId);
        if (storefront.isEmpty()) {
            log.warn("Vendor {} suspended but no storefront exists — nothing to reconcile", vendorId);
            return;
        }
        storefrontService.verify(storefront.get().storefrontId(),
                new StorefrontDtos.VerifyStorefrontRequest("SUSPENDED",
                        "Suspended by msika-flow vendor lifecycle event"));
        int suspended = 0;
        for (ListingDtos.ListingView listing
                : listingService.listForSeller(SELLER_TYPE_VENDOR, vendorId)) {
            if ("PUBLISHED".equals(listing.status())) {
                listingService.suspend(listing.listingId(), new ListingDtos.ModerationRequest(
                        "Seller suspended by msika-flow vendor lifecycle event"));
                suspended++;
            }
        }
        log.info("Vendor {} suspended — storefront {} suspended, {} published listing(s) suspended",
                vendorId, storefront.get().storefrontId(), suspended);
    }

    private static JsonNode unwrapPayload(JsonNode root) {
        if (root.has("payload") && root.get("payload").isObject()) {
            return root.get("payload");
        }
        if (root.has("data") && root.get("data").isObject()) {
            return root.get("data");
        }
        return root;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            if (node != null && node.hasNonNull(field)) {
                String v = node.get(field).asText();
                if (v != null && !v.isBlank()) {
                    return v;
                }
            }
        }
        return null;
    }

    private static UUID parseUuid(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
