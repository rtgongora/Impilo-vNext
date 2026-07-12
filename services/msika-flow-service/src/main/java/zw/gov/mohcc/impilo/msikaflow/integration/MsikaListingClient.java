package zw.gov.mohcc.impilo.msikaflow.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Real pricing source-of-truth client — msika-service listing lookup.
 *
 * <p>Replaces the checkout-time {@code BigDecimal.ZERO} hardcode: cart lines are
 * priced from the msika-service listing (never from a client-supplied price), so an
 * order can only be created with a real, currently-published listing price. A
 * missing/unpublished/unpriced listing returns {@link Optional#empty()} — the caller
 * (CartService.checkout) turns that into a 422 {@code ValidationFailedException} with
 * per-line detail rather than silently defaulting to zero.</p>
 */
@Service
public class MsikaListingClient {

    private static final Logger log = LoggerFactory.getLogger(MsikaListingClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public record ListingPrice(BigDecimal amount, String currency, String sellerType, String sellerId) {}

    public MsikaListingClient(ObjectMapper objectMapper,
                              @Value("${msika-flow.integration.msika-base-url:http://msika-service:8086}") String msikaBaseUrl) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder()
                .baseUrl(msikaBaseUrl.replaceAll("/$", ""))
                .build();
    }

    /**
     * GET /v1/listings/{listingId}. Returns empty when the listing is missing, the
     * upstream is unreachable, or the response carries no {@code priceAmount} — every
     * one of those is an honest "cannot price this line", not a zero price.
     */
    public Optional<ListingPrice> resolvePrice(String listingId) {
        if (listingId == null || listingId.isBlank()) {
            return Optional.empty();
        }
        try {
            String body = restClient.get()
                    .uri("/v1/listings/{id}", listingId)
                    .retrieve()
                    .body(String.class);
            if (body == null) {
                return Optional.empty();
            }
            JsonNode root = objectMapper.readTree(body);
            if (!root.path("success").asBoolean(false)) {
                return Optional.empty();
            }
            JsonNode data = root.path("data");
            if (data.isMissingNode() || !data.hasNonNull("priceAmount")) {
                return Optional.empty();
            }
            BigDecimal amount = new BigDecimal(data.get("priceAmount").asText());
            String currency = data.path("priceCurrency").asText("ZWG");
            // The listing's seller is the order's payee — carry it so a
            // citizen-buyer order (which has no facility context) can still
            // resolve a MusheX provider_id.
            String sellerType = data.path("sellerType").asText(null);
            String sellerId = data.path("sellerId").asText(null);
            return Optional.of(new ListingPrice(amount, currency, sellerType, sellerId));
        } catch (RestClientException e) {
            log.warn("Listing price lookup failed for {}: {}", listingId, e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("Listing price lookup unparseable for {}: {}", listingId, e.getMessage());
            return Optional.empty();
        }
    }
}
