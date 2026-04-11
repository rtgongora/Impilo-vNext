package zw.gov.mohcc.impilo.msikaflow.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class MushexClient {

    private static final Logger log = LoggerFactory.getLogger(MushexClient.class);

    private final RestTemplate restTemplate;
    private final String mushexBaseUrl;

    public MushexClient(@Value("${msika-flow.integration.mushex-url:http://localhost:8102}") String mushexBaseUrl) {
        this.restTemplate = new RestTemplate();
        this.mushexBaseUrl = mushexBaseUrl;
    }

    public String createPaymentIntent(String orderId, BigDecimal amount, String currency,
                                       String tenantId, Map<String, String> splits) {
        try {
            // In production, call MUSHEX API to create payment intent
            log.info("Creating MUSHEX payment intent: orderId={} amount={} {}", orderId, amount, currency);
            // Return simulated payment intent ID
            return "mpi_" + orderId;
        } catch (Exception e) {
            log.error("MUSHEX payment intent creation failed: {}", e.getMessage());
            throw new RuntimeException("Payment service unavailable", e);
        }
    }

    public String requestRefund(String paymentIntentId, BigDecimal amount, String reason) {
        try {
            log.info("Requesting MUSHEX refund: paymentIntentId={} amount={}", paymentIntentId, amount);
            return "mrf_" + paymentIntentId;
        } catch (Exception e) {
            log.error("MUSHEX refund request failed: {}", e.getMessage());
            throw new RuntimeException("Refund service unavailable", e);
        }
    }
}
