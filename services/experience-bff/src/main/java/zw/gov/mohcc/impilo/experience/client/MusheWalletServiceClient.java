package zw.gov.mohcc.impilo.experience.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import zw.gov.mohcc.impilo.experience.config.ServiceClientConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * HTTP client for the Mushe Wallet sovereign service.
 *
 * <p>Delegates wallet, merchant, hold, funding, and card lifecycle commands to
 * the Mushe Wallet service, which manages the canonical digital wallet for
 * patients, providers, and facilities within the Health OS.</p>
 *
 * <p>Trust headers are automatically forwarded by the RestTemplate's
 * header-forwarding interceptor configured in {@link ServiceClientConfig}.</p>
 */
@Component
public class MusheWalletServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MusheWalletServiceClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public MusheWalletServiceClient(RestTemplate serviceRestTemplate,
                                    ServiceClientConfig.ServiceEndpoints endpoints,
                                    ObjectMapper objectMapper) {
        this.restTemplate = serviceRestTemplate;
        this.baseUrl = endpoints.musheWalletBaseUrl();
        this.objectMapper = objectMapper;
    }

    // ── Wallet CRUD & Transactions ─────────────────────────────────────

    /**
     * Create a new wallet.
     *
     * @param ownerType   the owner type (PERSON, PROVIDER, FACILITY)
     * @param ownerRef    the owner reference identifier
     * @param displayName display name for the wallet
     * @param currency    ISO 4217 currency code
     * @return the created wallet response
     */
    public JsonNode createWallet(String ownerType, String ownerRef,
                                 String displayName, String currency) {
        String url = baseUrl + "/internal/v1/wallets";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ownerType", ownerType);
        body.put("ownerRef", ownerRef);
        if (displayName != null) body.put("displayName", displayName);
        if (currency != null) body.put("currency", currency);

        log.info("MusheWallet: Creating wallet for ownerType={}, ownerRef={}",
                ownerType, ownerRef);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a wallet by its ID.
     *
     * @param walletId the wallet UUID
     * @return the wallet entity
     */
    public JsonNode getWallet(UUID walletId) {
        String url = baseUrl + "/internal/v1/wallets/" + walletId;
        log.debug("MusheWallet: Getting wallet={}", walletId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a wallet by owner type and reference.
     *
     * @param ownerType the owner type
     * @param ownerRef  the owner reference
     * @return the wallet entity
     */
    public JsonNode getWalletByOwner(String ownerType, String ownerRef) {
        String url = baseUrl + "/internal/v1/wallets/by-owner?owner_type=" + ownerType
                + "&owner_ref=" + ownerRef;
        log.debug("MusheWallet: Getting wallet by owner ownerType={}, ownerRef={}", ownerType, ownerRef);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get the available balance of a wallet.
     *
     * @param walletId the wallet UUID
     * @return the balance response
     */
    public JsonNode getBalance(UUID walletId) {
        String url = baseUrl + "/internal/v1/wallets/" + walletId + "/balance";
        log.debug("MusheWallet: Getting balance for wallet={}", walletId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get paginated transactions for a wallet.
     *
     * @param walletId the wallet UUID
     * @param page     page number (0-based)
     * @param size     page size
     * @return the paginated transaction list
     */
    public JsonNode getTransactions(UUID walletId, int page, int size) {
        String url = baseUrl + "/internal/v1/wallets/" + walletId
                + "/transactions?page=" + page + "&size=" + size;
        log.debug("MusheWallet: Getting transactions for wallet={}, page={}, size={}", walletId, page, size);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Credit a wallet.
     *
     * @param walletId         the wallet UUID
     * @param amount           credit amount
     * @param txnType          transaction type
     * @param channel          transaction channel
     * @param reference        external reference
     * @param description      description
     * @param counterpartyRef  counterparty reference
     * @param counterpartyName counterparty display name
     * @return the created transaction
     */
    public JsonNode creditWallet(UUID walletId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/wallets/" + walletId + "/credit";
        log.info("MusheWallet: Crediting wallet={}", walletId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Debit a wallet.
     *
     * @param walletId the wallet UUID
     * @param body     debit request body
     * @return the created transaction
     */
    public JsonNode debitWallet(UUID walletId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/wallets/" + walletId + "/debit";
        log.info("MusheWallet: Debiting wallet={}", walletId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Transfer between two wallets.
     *
     * @param body transfer request body (fromWalletId, toWalletId, amount, etc.)
     * @return the created transaction
     */
    public JsonNode transfer(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/wallets/transfer";
        log.info("MusheWallet: Transfer from={} to={}",
                body.get("fromWalletId"), body.get("toWalletId"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Merchant ───────────────────────────────────────────────────────

    /**
     * Register a new merchant account.
     *
     * @param body merchant registration request
     * @return the created merchant account
     */
    public JsonNode registerMerchant(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/merchants";
        log.info("MusheWallet: Registering merchant providerNumber={}", body.get("providerNumber"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a merchant by its ID.
     *
     * @param merchantId the merchant UUID
     * @return the merchant entity
     */
    public JsonNode getMerchant(UUID merchantId) {
        String url = baseUrl + "/internal/v1/merchants/" + merchantId;
        log.debug("MusheWallet: Getting merchant={}", merchantId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a merchant by provider number.
     *
     * @param providerNumber the provider number
     * @return the merchant entity
     */
    public JsonNode getMerchantByProvider(String providerNumber) {
        String url = baseUrl + "/internal/v1/merchants/by-provider?provider_number=" + providerNumber;
        log.debug("MusheWallet: Getting merchant by providerNumber={}", providerNumber);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Pay a merchant from a wallet.
     *
     * @param body payment request (fromWalletId, merchantProviderNumber, amount, etc.)
     * @return the created transaction
     */
    public JsonNode payMerchant(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/merchants/pay";
        log.info("MusheWallet: Paying merchant providerNumber={} from wallet={}",
                body.get("merchantProviderNumber"), body.get("fromWalletId"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Holds ──────────────────────────────────────────────────────────

    /**
     * Place a hold on wallet funds.
     *
     * @param body hold request (walletId, amount, reason, reference, expiresAt)
     * @return the created hold
     */
    public JsonNode placeHold(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/holds";
        log.info("MusheWallet: Placing hold on wallet={}, amount={}",
                body.get("walletId"), body.get("amount"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Capture a previously placed hold.
     *
     * @param holdId the hold UUID
     * @return the captured hold
     */
    public JsonNode captureHold(UUID holdId) {
        String url = baseUrl + "/internal/v1/holds/" + holdId + "/capture";
        log.info("MusheWallet: Capturing hold={}", holdId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Release a previously placed hold.
     *
     * @param holdId the hold UUID
     * @return the released hold
     */
    public JsonNode releaseHold(UUID holdId) {
        String url = baseUrl + "/internal/v1/holds/" + holdId + "/release";
        log.info("MusheWallet: Releasing hold={}", holdId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    // ── Funding Sources & Deposits ─────────────────────────────────────

    /**
     * Add a funding source to a wallet.
     *
     * @param walletId the wallet UUID
     * @param body     funding source request (sourceType, provider, accountRef, accountName)
     * @return the created funding source
     */
    public JsonNode addFundingSource(UUID walletId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/funding/" + walletId + "/sources";
        log.info("MusheWallet: Adding funding source to wallet={}, type={}",
                walletId, body.get("sourceType"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * List all funding sources for a wallet.
     *
     * @param walletId the wallet UUID
     * @return the list of funding sources
     */
    public JsonNode listFundingSources(UUID walletId) {
        String url = baseUrl + "/internal/v1/funding/" + walletId + "/sources";
        log.debug("MusheWallet: Listing funding sources for wallet={}", walletId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Deposit funds from a linked funding source.
     *
     * @param walletId the wallet UUID
     * @param body     deposit request (sourceId, amount, reference)
     * @return the created transaction
     */
    public JsonNode deposit(UUID walletId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/funding/" + walletId + "/deposit";
        log.info("MusheWallet: Depositing to wallet={} from source={}", walletId, body.get("sourceId"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * List deposit intents (pending + confirmed) for a wallet.
     */
    public JsonNode listDeposits(UUID walletId) {
        String url = baseUrl + "/internal/v1/funding/" + walletId + "/deposits";
        log.debug("MusheWallet: Listing deposits for wallet={}", walletId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Deposit cash into a wallet.
     *
     * @param walletId the wallet UUID
     * @param body     cash deposit request (cashierId, facilityId, amount, reference)
     * @return the created transaction
     */
    public JsonNode cashDeposit(UUID walletId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/funding/" + walletId + "/cash-deposit";
        log.info("MusheWallet: Cash deposit to wallet={}, cashier={}", walletId, body.get("cashierId"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    // ── Cards ──────────────────────────────────────────────────────────

    /**
     * Issue a new card for a wallet.
     *
     * @param body card issuance request (walletId, cardType, cardNetwork)
     * @return the issued card response
     */
    public JsonNode issueCard(Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/cards";
        log.info("MusheWallet: Issuing card for wallet={}", body.get("walletId"));
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get a card by its ID.
     *
     * @param cardId the card UUID
     * @return the card details
     */
    public JsonNode getCard(UUID cardId) {
        String url = baseUrl + "/internal/v1/cards/" + cardId;
        log.debug("MusheWallet: Getting card={}", cardId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Activate a card with a PIN.
     *
     * @param cardId the card UUID
     * @param body   activation request (pin)
     * @return the activated card
     */
    public JsonNode activateCard(UUID cardId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/cards/" + cardId + "/activate";
        log.info("MusheWallet: Activating card={}", cardId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Block a card.
     *
     * @param cardId the card UUID
     * @param body   block request (reason)
     * @return the blocked card
     */
    public JsonNode blockCard(UUID cardId, Map<String, Object> body) {
        String url = baseUrl + "/internal/v1/cards/" + cardId + "/block";
        log.info("MusheWallet: Blocking card={}", cardId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, body, JsonNode.class);
        return extractData(response);
    }

    /**
     * Unblock a card.
     *
     * @param cardId the card UUID
     * @return the unblocked card
     */
    public JsonNode unblockCard(UUID cardId) {
        String url = baseUrl + "/internal/v1/cards/" + cardId + "/unblock";
        log.info("MusheWallet: Unblocking card={}", cardId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Replace a card with a new one.
     *
     * @param cardId the card UUID to replace
     * @return the replacement card
     */
    public JsonNode replaceCard(UUID cardId) {
        String url = baseUrl + "/internal/v1/cards/" + cardId + "/replace";
        log.info("MusheWallet: Replacing card={}", cardId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get all cards for a wallet.
     *
     * @param walletId the wallet UUID
     * @return the cards list
     */
    public JsonNode getCardsByWallet(UUID walletId) {
        String url = baseUrl + "/internal/v1/cards/by-wallet/" + walletId;
        log.debug("MusheWallet: Getting cards for wallet={}", walletId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get health data stored on a card.
     *
     * @param cardId the card UUID
     * @return the health data metadata list
     */
    public JsonNode getCardHealthData(UUID cardId) {
        String url = baseUrl + "/internal/v1/cards/" + cardId + "/health-data";
        log.debug("MusheWallet: Getting health data for card={}", cardId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    /**
     * Trigger health data synchronization for a card.
     *
     * @param cardId the card UUID
     * @return the sync status response
     */
    public JsonNode syncHealthData(UUID cardId) {
        String url = baseUrl + "/internal/v1/cards/" + cardId + "/health-data/sync";
        log.info("MusheWallet: Triggering health data sync for card={}", cardId);
        ResponseEntity<JsonNode> response = restTemplate.postForEntity(url, null, JsonNode.class);
        return extractData(response);
    }

    /**
     * Get pending card update queue entries.
     *
     * @param cardId the card UUID
     * @return the pending updates list
     */
    public JsonNode getPendingUpdates(UUID cardId) {
        String url = baseUrl + "/internal/v1/cards/" + cardId + "/pending-updates";
        log.debug("MusheWallet: Getting pending updates for card={}", cardId);
        ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);
        return extractData(response);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private JsonNode extractData(ResponseEntity<JsonNode> response) {
        if (response.getBody() != null && response.getBody().has("data")) {
            return response.getBody().get("data");
        }
        return response.getBody();
    }
}
