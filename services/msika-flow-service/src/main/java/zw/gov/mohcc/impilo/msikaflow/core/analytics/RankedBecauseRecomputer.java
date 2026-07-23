package zw.gov.mohcc.impilo.msikaflow.core.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import zw.gov.mohcc.impilo.msikaflow.core.EligibilityService;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.FulfillmentOfferLineEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.entity.MarketplaceRequestEntity;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.FulfillmentOfferLineRepository;
import zw.gov.mohcc.impilo.msikaflow.persistence.repository.VendorProfileRepository;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OF-B29 §21 — deterministic recomputation of the ranked-because label set
 * over PERSISTED request/offer data, mirroring the declared comparison rules
 * of {@code MarketplaceRequestService.listOffers} (SUITABLE base, CHEAPEST,
 * NEAREST via zone coverage, COMPLETE = all lines at quantity with no
 * substitution) and the declared sort (§4.5: completeness first, then price —
 * never price alone).
 *
 * <p>Read-only by doctrine: this class never mutates marketplace state. It is
 * intentionally a separate mirror rather than a refactor of the request
 * machinery (owned by another lane); the audit's job is exactly to recompute
 * from record and compare against what was captured.</p>
 */
@Component
public class RankedBecauseRecomputer {

    private static final Logger log = LoggerFactory.getLogger(RankedBecauseRecomputer.class);

    public static final String LABEL_SUITABLE = "SUITABLE";
    public static final String LABEL_CHEAPEST = "CHEAPEST";
    public static final String LABEL_NEAREST = "NEAREST";
    public static final String LABEL_COMPLETE = "COMPLETE";

    private final FulfillmentOfferLineRepository offerLineRepository;
    private final VendorProfileRepository vendorRepository;
    private final EligibilityService eligibilityService;
    private final ObjectMapper objectMapper;

    public RankedBecauseRecomputer(FulfillmentOfferLineRepository offerLineRepository,
                                   VendorProfileRepository vendorRepository,
                                   EligibilityService eligibilityService,
                                   ObjectMapper objectMapper) {
        this.offerLineRepository = offerLineRepository;
        this.vendorRepository = vendorRepository;
        this.eligibilityService = eligibilityService;
        this.objectMapper = objectMapper;
    }

    /** One recomputed comparison row: offer id → (1-based position, labels). */
    public record RankedRow(String offerId, int position, List<String> labels, BigDecimal priceTotal) {}

    /**
     * Recompute positions + labels for the given candidate offers of one
     * request. The caller chooses the candidate set (ACTIVE offers for live
     * capture; ACTIVE/COMMITTED/NOT_SELECTED/EXPIRED for post-hoc audit —
     * approximating the comparison set as it stood, a documented limitation
     * until commitment-time capture lands).
     */
    public List<RankedRow> recompute(MarketplaceRequestEntity request,
                                     List<FulfillmentOfferEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> requestedQuantities = requestLineQuantities(request);
        BigDecimal cheapest = candidates.stream()
                .map(FulfillmentOfferEntity::getPriceTotal)
                .filter(java.util.Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);

        record Scored(FulfillmentOfferEntity offer, List<String> labels) {}
        List<Scored> scored = new ArrayList<>();
        for (FulfillmentOfferEntity offer : candidates) {
            List<FulfillmentOfferLineEntity> lines = offerLineRepository.findByOfferId(offer.getOfferId());
            List<String> labels = new ArrayList<>();
            labels.add(LABEL_SUITABLE);
            if (cheapest != null && offer.getPriceTotal() != null
                    && offer.getPriceTotal().compareTo(cheapest) == 0) {
                labels.add(LABEL_CHEAPEST);
            }
            if (isNearest(offer, request)) {
                labels.add(LABEL_NEAREST);
            }
            if (coversAllLines(lines, requestedQuantities)) {
                labels.add(LABEL_COMPLETE);
            }
            scored.add(new Scored(offer, labels));
        }
        // Declared default sort mirror: completeness first, then price (§4.5).
        scored.sort(Comparator
                .comparing((Scored s) -> !s.labels().contains(LABEL_COMPLETE))
                .thenComparing(s -> s.offer().getPriceTotal(),
                        Comparator.nullsLast(Comparator.naturalOrder())));

        List<RankedRow> rows = new ArrayList<>();
        for (int i = 0; i < scored.size(); i++) {
            Scored s = scored.get(i);
            rows.add(new RankedRow(s.offer().getOfferId(), i + 1,
                    List.copyOf(s.labels()), s.offer().getPriceTotal()));
        }
        return rows;
    }

    private boolean isNearest(FulfillmentOfferEntity offer, MarketplaceRequestEntity request) {
        try {
            return vendorRepository.findById(offer.getVendorId())
                    .filter(v -> request.getTenantId().equals(v.getTenantId()))
                    .map(v -> eligibilityService.coversZone(v, request.getCoarseZone()))
                    .orElse(false);
        } catch (Exception e) {
            log.warn("NEAREST recompute degraded for offer {}: {}", offer.getOfferId(), e.getMessage());
            return false;
        }
    }

    private boolean coversAllLines(List<FulfillmentOfferLineEntity> lines,
                                   Map<String, Integer> requestedQuantities) {
        if (requestedQuantities.isEmpty()) {
            return false;
        }
        if (lines.stream().anyMatch(FulfillmentOfferLineEntity::isSubstitutionProposed)) {
            return false;
        }
        Map<String, Integer> offered = new HashMap<>();
        for (FulfillmentOfferLineEntity line : lines) {
            offered.merge(line.getRequestLineRef(), line.getQuantity(), Integer::sum);
        }
        for (Map.Entry<String, Integer> requested : requestedQuantities.entrySet()) {
            Integer qty = offered.get(requested.getKey());
            if (qty == null || qty < requested.getValue()) {
                return false;
            }
        }
        return true;
    }

    /** Published-snapshot line refs → requested quantities (request-side truth mirror). */
    Map<String, Integer> requestLineQuantities(MarketplaceRequestEntity request) {
        Map<String, Integer> quantities = new LinkedHashMap<>();
        try {
            JsonNode snapshot = objectMapper.readTree(request.getPublishedLinesJson());
            for (JsonNode line : snapshot.path("lines")) {
                quantities.put(line.path("lineRef").asText(), line.path("quantity").asInt(1));
            }
        } catch (Exception e) {
            log.warn("Unparseable published snapshot for request {}: {}",
                    request.getRequestId(), e.getMessage());
        }
        return quantities;
    }
}
