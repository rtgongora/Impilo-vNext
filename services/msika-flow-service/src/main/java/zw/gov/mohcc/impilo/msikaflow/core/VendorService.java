package zw.gov.mohcc.impilo.msikaflow.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import zw.gov.mohcc.impilo.msikaflow.domain.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.*;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.*;

import java.util.*;

@Service
public class VendorService {

    private static final Logger log = LoggerFactory.getLogger(VendorService.class);

    private final VendorProfileRepository vendorProfileRepository;
    private final VendorDocumentRepository vendorDocumentRepository;
    private final OpsReviewRepository opsReviewRepository;
    private final EventOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public VendorService(VendorProfileRepository vendorProfileRepository,
                         VendorDocumentRepository vendorDocumentRepository,
                         OpsReviewRepository opsReviewRepository,
                         EventOutboxRepository outboxRepository,
                         ObjectMapper objectMapper) {
        this.vendorProfileRepository = vendorProfileRepository;
        this.vendorDocumentRepository = vendorDocumentRepository;
        this.opsReviewRepository = opsReviewRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public VendorProfileEntity applyVendor(UUID tenantId, String name, VendorType type, String coverage) {
        VendorProfileEntity vendor = new VendorProfileEntity();
        vendor.setVendorId(UlidGenerator.generate());
        vendor.setTenantId(tenantId);
        vendor.setName(name);
        vendor.setType(type);
        vendor.setStatus(VendorStatus.APPLIED);
        vendor.setCoverage(coverage);

        vendorProfileRepository.save(vendor);

        // Create ops review
        OpsReviewEntity review = new OpsReviewEntity();
        review.setId(UlidGenerator.generate());
        review.setEntityType("VENDOR");
        review.setEntityId(vendor.getVendorId());
        review.setStatus(ReviewStatus.PENDING);
        review.setTenantId(tenantId);
        opsReviewRepository.save(review);

        publishOutbox("Vendor", vendor.getVendorId(), "VENDOR_APPLIED", tenantId,
                Map.of("vendorId", vendor.getVendorId(), "name", name, "type", type.name()));

        log.info("Vendor application: id={} name={} type={}", vendor.getVendorId(), name, type);
        return vendor;
    }

    @Transactional
    public VendorDocumentEntity uploadDocument(String vendorId, String docType, String landelaDocId) {
        VendorProfileEntity vendor = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));

        VendorDocumentEntity doc = new VendorDocumentEntity();
        doc.setId(UlidGenerator.generate());
        doc.setVendorId(vendorId);
        doc.setDocType(docType);
        doc.setLandelaDocId(landelaDocId);
        doc.setStatus(VendorDocStatus.UPLOADED);

        vendorDocumentRepository.save(doc);

        // Move vendor to PENDING_VERIFICATION if still APPLIED
        if (vendor.getStatus() == VendorStatus.APPLIED) {
            vendor.setStatus(VendorStatus.PENDING_VERIFICATION);
            vendorProfileRepository.save(vendor);
        }

        log.info("Vendor document uploaded: vendorId={} docType={}", vendorId, docType);
        return doc;
    }

    @Transactional
    public VendorProfileEntity approveVendor(String vendorId, String actorId, UUID tenantId) {
        VendorProfileEntity vendor = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));

        vendor.setStatus(VendorStatus.ACTIVE);
        vendorProfileRepository.save(vendor);

        publishOutbox("Vendor", vendorId, "VENDOR_APPROVED", tenantId,
                Map.of("vendorId", vendorId, "approvedBy", actorId));

        log.info("Vendor approved: id={}", vendorId);
        return vendor;
    }

    /** Sets a vendor to REJECTED (ops decision on the VENDOR review). */
    @Transactional
    public VendorProfileEntity rejectVendor(String vendorId, String actorId, UUID tenantId, String notes) {
        VendorProfileEntity vendor = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));

        vendor.setStatus(VendorStatus.REJECTED);
        vendorProfileRepository.save(vendor);

        publishOutbox("Vendor", vendorId, "VENDOR_REJECTED", tenantId,
                Map.of("vendorId", vendorId, "rejectedBy", actorId, "notes", notes != null ? notes : ""));

        log.info("Vendor rejected: id={} notes={}", vendorId, notes);
        return vendor;
    }

    @Transactional
    public VendorProfileEntity suspendVendor(String vendorId, String reason, String actorId, UUID tenantId) {
        VendorProfileEntity vendor = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));

        vendor.setStatus(VendorStatus.SUSPENDED);
        vendorProfileRepository.save(vendor);

        publishOutbox("Vendor", vendorId, "VENDOR_SUSPENDED", tenantId,
                Map.of("vendorId", vendorId, "reason", reason, "suspendedBy", actorId));

        log.info("Vendor suspended: id={} reason={}", vendorId, reason);
        return vendor;
    }

    /** Reverses a suspension: SUSPENDED → ACTIVE, ops-gated (VendorController/OpsController). */
    @Transactional
    public VendorProfileEntity reinstateVendor(String vendorId, String actorId, UUID tenantId) {
        VendorProfileEntity vendor = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));

        if (vendor.getStatus() != VendorStatus.SUSPENDED) {
            throw new IllegalStateException("Vendor is not SUSPENDED: " + vendor.getStatus());
        }

        vendor.setStatus(VendorStatus.ACTIVE);
        vendorProfileRepository.save(vendor);

        publishOutbox("Vendor", vendorId, "VENDOR_REINSTATED", tenantId,
                Map.of("vendorId", vendorId, "reinstatedBy", actorId));

        log.info("Vendor reinstated: id={}", vendorId);
        return vendor;
    }

    /** Binds a JWT principal (actor id) to a vendor profile — the BFF's vendor-me seam. Ops-gated. */
    @Transactional
    public VendorProfileEntity bindActor(String vendorId, String actorId, String boundBy) {
        VendorProfileEntity vendor = vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));

        vendor.setActorBinding(actorId);
        vendorProfileRepository.save(vendor);

        publishOutbox("Vendor", vendorId, "VENDOR_ACTOR_BOUND", vendor.getTenantId(),
                Map.of("vendorId", vendorId, "actorId", actorId, "boundBy", boundBy != null ? boundBy : ""));

        log.info("Vendor actor bound: vendorId={} actorId={}", vendorId, actorId);
        return vendor;
    }

    public VendorProfileEntity getByActorBinding(UUID tenantId, String actorId) {
        return vendorProfileRepository.findByTenantIdAndActorBinding(tenantId, actorId)
                .orElseThrow(() -> new IllegalArgumentException("No vendor bound to actor: " + actorId));
    }

    public VendorProfileEntity getVendor(String vendorId) {
        return vendorProfileRepository.findById(vendorId)
                .orElseThrow(() -> new IllegalArgumentException("Vendor not found: " + vendorId));
    }

    public Page<VendorProfileEntity> listVendors(UUID tenantId, VendorStatus status, Pageable pageable) {
        if (status != null) {
            return vendorProfileRepository.findByTenantIdAndStatus(tenantId, status, pageable);
        }
        return vendorProfileRepository.findByTenantId(tenantId, pageable);
    }

    public List<VendorDocumentEntity> getVendorDocuments(String vendorId) {
        return vendorDocumentRepository.findByVendorId(vendorId);
    }

    private void publishOutbox(String aggregateType, String aggregateId, String eventType,
                               UUID tenantId, Map<String, Object> data) {
        try {
            EventOutboxEntity outbox = new EventOutboxEntity();
            outbox.setAggregateType(aggregateType);
            outbox.setAggregateId(aggregateId);
            outbox.setEventType(eventType);
            outbox.setPayload(objectMapper.writeValueAsString(data));
            outbox.setTenantId(tenantId);
            outboxRepository.save(outbox);
        } catch (Exception e) {
            log.error("Failed to write outbox: {}", e.getMessage());
        }
    }
}
